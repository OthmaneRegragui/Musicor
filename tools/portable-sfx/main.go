// Command portable-sfx builds Musicor's single-file portable Windows executable.
//
// Compose Desktop can only produce a Windows *installer* (`packageExe`) or an
// app-image *directory* (`createDistributable`) - never a single portable .exe.
// To still ship one file, the app-image directory is zipped up, embedded into
// this program, and the result is a self-extracting executable: on first run it
// unpacks the app-image into a per-build cache directory and starts the jpackage
// launcher, and later runs reuse the unpacked copy so start-up stays fast.
//
// The payload is embedded with go:embed, so build it like this:
//
//	7z a -tzip -mx=1 payload.zip <appImageDir>\*
//	go build -ldflags "-H windowsgui -X main.payloadID=<sha256-of-payload.zip>" \
//	    -o Musicor.exe .
//
// -H windowsgui keeps a console window from opening next to the player;
// failures are written to <cache>/Musicor/launcher.log instead of stderr.
//
// payloadID has to change whenever the payload changes, otherwise a stale cache
// directory from an older release would be reused. A hash of the payload is the
// simplest way to guarantee that.
package main

import (
	"archive/zip"
	"bytes"
	// Required by go:embed below; the payload is embedded as a plain []byte.
	_ "embed"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"time"
)

//go:embed payload.zip
var payload []byte

// payloadID names the cache directory holding the unpacked app-image, so a new
// release unpacks next to the old one instead of overwriting it in place.
// Overwritten in at build time via -ldflags "-X main.payloadID=...".
var payloadID = "dev"

// launcherRelPath is the launcher to start, relative to the unpacked root.
// jpackage puts "<packageName>.exe" in the root of a Windows app-image. When
// left empty the launcher is auto-detected as the single .exe in that root.
var launcherRelPath = ""

// completeMarker is written into the cache directory once the payload has been
// unpacked in full, so an interrupted run is unpacked again instead of being
// mistaken for a finished one.
const completeMarker = ".musicor-unpacked"

func main() {
	if err := run(); err != nil {
		// Built as a GUI subsystem binary (-H windowsgui) so no console window
		// is left sitting next to the app, which means stderr is not visible
		// either. Mirror the failure into a log the user can be pointed at.
		fmt.Fprintln(os.Stderr, "Musicor: "+err.Error())
		logFailure(err)
		os.Exit(1)
	}
}

// logFailure appends a failure to <cache>/Musicor/launcher.log, best effort.
func logFailure(err error) {
	cacheDir, cacheErr := os.UserCacheDir()
	if cacheErr != nil {
		return
	}
	dir := filepath.Join(cacheDir, "Musicor")
	if mkdirErr := os.MkdirAll(dir, 0o755); mkdirErr != nil {
		return
	}
	f, openErr := os.OpenFile(filepath.Join(dir, "launcher.log"),
		os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0o644)
	if openErr != nil {
		return
	}
	defer f.Close()
	fmt.Fprintf(f, "%s: %s\n", time.Now().Format(time.RFC3339), err)
}

func run() error {
	root, err := prepareRoot()
	if err != nil {
		return err
	}
	launcher, err := findLauncher(root)
	if err != nil {
		return err
	}
	return start(launcher)
}

// prepareRoot returns the directory holding the unpacked app-image, unpacking
// the embedded payload first if that has not happened for this payloadID yet.
func prepareRoot() (string, error) {
	cacheDir, err := os.UserCacheDir()
	if err != nil {
		return "", fmt.Errorf("locating cache directory: %w", err)
	}
	root := filepath.Join(cacheDir, "Musicor", sanitize(payloadID))

	if unpacked(root) {
		return root, nil
	}
	// Unpack into a sibling directory first so that a crash or a full disk never
	// leaves a half-written directory that looks complete on the next run.
	staging := root + ".unpacking"
	if err := os.RemoveAll(staging); err != nil {
		return "", fmt.Errorf("clearing staging directory: %w", err)
	}
	if err := unpack(staging); err != nil {
		os.RemoveAll(staging)
		return "", err
	}
	if err := os.WriteFile(filepath.Join(staging, completeMarker), nil, 0o644); err != nil {
		os.RemoveAll(staging)
		return "", fmt.Errorf("writing completion marker: %w", err)
	}
	if err := os.RemoveAll(root); err != nil {
		os.RemoveAll(staging)
		return "", fmt.Errorf("clearing previous version: %w", err)
	}
	if err := os.Rename(staging, root); err != nil {
		os.RemoveAll(staging)
		return "", fmt.Errorf("installing unpacked payload: %w", err)
	}
	return root, nil
}

// unpack expands the embedded payload into dir.
func unpack(dir string) error {
	r, err := zip.NewReader(bytes.NewReader(payload), int64(len(payload)))
	if err != nil {
		return fmt.Errorf("reading embedded payload: %w", err)
	}
	for _, f := range r.File {
		target, err := safeJoin(dir, f.Name)
		if err != nil {
			return err
		}
		if f.FileInfo().IsDir() {
			if err := os.MkdirAll(target, 0o755); err != nil {
				return fmt.Errorf("creating %s: %w", target, err)
			}
			continue
		}
		if err := os.MkdirAll(filepath.Dir(target), 0o755); err != nil {
			return fmt.Errorf("creating %s: %w", filepath.Dir(target), err)
		}
		if err := writeFile(target, f); err != nil {
			return err
		}
	}
	return nil
}

// safeJoin resolves name inside dir, rejecting entries that would escape it
// (a crafted archive must not be able to write outside the cache directory).
func safeJoin(dir, name string) (string, error) {
	clean := filepath.Clean(filepath.FromSlash(name))
	if filepath.IsAbs(clean) || clean == ".." || strings.HasPrefix(clean, ".."+string(filepath.Separator)) {
		return "", fmt.Errorf("payload entry %q escapes the destination directory", name)
	}
	return filepath.Join(dir, clean), nil
}

func writeFile(target string, f *zip.File) error {
	src, err := f.Open()
	if err != nil {
		return fmt.Errorf("reading %s from payload: %w", f.Name, err)
	}
	defer src.Close()

	dst, err := os.OpenFile(target, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, f.Mode().Perm())
	if err != nil {
		return fmt.Errorf("creating %s: %w", target, err)
	}
	if _, err := io.Copy(dst, src); err != nil {
		dst.Close()
		return fmt.Errorf("writing %s: %w", target, err)
	}
	return dst.Close()
}

func unpacked(root string) bool {
	if _, err := os.Stat(filepath.Join(root, completeMarker)); err != nil {
		return false
	}
	// A present marker with no payload beside it means the user cleared the
	// app but not the marker; unpack again rather than failing to start.
	entries, err := os.ReadDir(root)
	return err == nil && len(entries) > 1
}

// findLauncher locates the jpackage launcher inside the unpacked app-image.
func findLauncher(root string) (string, error) {
	if launcherRelPath != "" {
		launcher := filepath.Join(root, filepath.FromSlash(launcherRelPath))
		if _, err := os.Stat(launcher); err != nil {
			return "", fmt.Errorf("launcher %q not found in payload: %w", launcherRelPath, err)
		}
		return launcher, nil
	}

	entries, err := os.ReadDir(root)
	if err != nil {
		return "", fmt.Errorf("reading unpacked payload: %w", err)
	}
	var found []string
	for _, e := range entries {
		if !e.IsDir() && strings.EqualFold(filepath.Ext(e.Name()), ".exe") {
			found = append(found, e.Name())
		}
	}
	switch len(found) {
	case 1:
		return filepath.Join(root, found[0]), nil
	case 0:
		return "", errors.New("no launcher .exe found next to the payload root")
	default:
		return "", fmt.Errorf("payload root has several launchers (%s); build with -ldflags \"-X main.launcherRelPath=<name>\"", strings.Join(found, ", "))
	}
}

// start runs the launcher and exits with its status, so closing the app also
// closes the portable executable.
func start(launcher string) error {
	cmd := exec.Command(launcher, os.Args[1:]...)
	cmd.Dir = filepath.Dir(launcher)
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	cmd.Stdin = os.Stdin

	if err := cmd.Run(); err != nil {
		var exit *exec.ExitError
		if errors.As(err, &exit) {
			os.Exit(exit.ExitCode())
		}
		return fmt.Errorf("starting %s: %w", filepath.Base(launcher), err)
	}
	return nil
}

// sanitize keeps payloadID usable as a single path segment.
func sanitize(id string) string {
	var b strings.Builder
	for _, r := range id {
		switch {
		case r >= 'a' && r <= 'z', r >= 'A' && r <= 'Z', r >= '0' && r <= '9', r == '-', r == '_':
			b.WriteRune(r)
		default:
			b.WriteByte('_')
		}
	}
	if b.Len() == 0 {
		return "dev"
	}
	return b.String()
}
