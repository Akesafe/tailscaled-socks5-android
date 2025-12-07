package appctr

import (
	"bufio"
	"context"
	"fmt"
	"log/slog"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"sync"
	"syscall"
	"time"

	_ "golang.org/x/mobile/bind"
)

var cmd *exec.Cmd
var PC pathControl

// LogManager manages log storage and retrieval
type LogManager struct {
	mu      sync.RWMutex
	logs    []string
	maxSize int
}

var logManager = &LogManager{
	logs:    make([]string, 0, 10000),
	maxSize: 10000,
}

// AddLog adds a log entry
func (lm *LogManager) AddLog(entry string) {
	lm.mu.Lock()
	defer lm.mu.Unlock()

	// Trim if exceeds max size
	if len(lm.logs) >= lm.maxSize {
		lm.logs = lm.logs[len(lm.logs)/2:]
	}
	lm.logs = append(lm.logs, entry)
}

// GetLogs returns all logs as a single string
func (lm *LogManager) GetLogs() string {
	lm.mu.RLock()
	defer lm.mu.RUnlock()
	return strings.Join(lm.logs, "\n")
}

// GetLogCount returns the number of log entries
func (lm *LogManager) GetLogCount() int {
	lm.mu.RLock()
	defer lm.mu.RUnlock()
	return len(lm.logs)
}

// ClearLogs clears all logs
func (lm *LogManager) ClearLogs() {
	lm.mu.Lock()
	defer lm.mu.Unlock()
	lm.logs = make([]string, 0, 10000)
}

// GetLogs returns all logs as a string (exported for Android)
func GetLogs() string {
	return logManager.GetLogs()
}

// GetLogCount returns the number of log entries (exported for Android)
func GetLogCount() int {
	return logManager.GetLogCount()
}

// ClearLogs clears all logs (exported for Android)
func ClearLogs() {
	logManager.ClearLogs()
}

// dualHandler is a custom slog.Handler that writes to both stdout and LogManager
type dualHandler struct {
	textHandler slog.Handler
}

func newDualHandler() *dualHandler {
	return &dualHandler{
		textHandler: slog.NewTextHandler(os.Stdout, &slog.HandlerOptions{
			Level: slog.LevelDebug,
		}),
	}
}

func (h *dualHandler) Enabled(ctx context.Context, level slog.Level) bool {
	return true
}

func (h *dualHandler) Handle(ctx context.Context, r slog.Record) error {
	// Format log entry for LogManager
	timestamp := r.Time.Format("2006-01-02 15:04:05.000")
	level := r.Level.String()

	// Build message with attributes
	var sb strings.Builder
	sb.WriteString(r.Message)
	r.Attrs(func(a slog.Attr) bool {
		sb.WriteString(" ")
		sb.WriteString(a.Key)
		sb.WriteString("=")
		sb.WriteString(fmt.Sprintf("%v", a.Value.Any()))
		return true
	})

	entry := fmt.Sprintf("[%s] [%s] %s", timestamp, level, sb.String())
	logManager.AddLog(entry)

	// Also write to stdout
	return h.textHandler.Handle(ctx, r)
}

func (h *dualHandler) WithAttrs(attrs []slog.Attr) slog.Handler {
	return &dualHandler{
		textHandler: h.textHandler.WithAttrs(attrs),
	}
}

func (h *dualHandler) WithGroup(name string) slog.Handler {
	return &dualHandler{
		textHandler: h.textHandler.WithGroup(name),
	}
}

func init() {
	// Set custom slog handler at startup
	slog.SetDefault(slog.New(newDualHandler()))
}

type Closer interface {
	Close() error
}

func IsRunning() bool { return cmd != nil && cmd.Process != nil }

type StartOptions struct {
	ExecPath      string
	SocketPath    string
	StatePath     string
	Socks5Server  string
	CloseCallBack Closer
	AuthKey       string
}

func Start(opt *StartOptions) {
	if IsRunning() {
		return
	}
	if opt.Socks5Server == "" {
		opt.Socks5Server = ":1055"
	}
	PC = newPathControl(opt.ExecPath, opt.SocketPath, opt.StatePath)
	go func() {
		err := tailscaledCmd(PC, opt.Socks5Server)
		if err != nil {
			slog.Error("tailscaled cmd", "err", err)
		}
		Stop()
		if opt.CloseCallBack != nil {
			opt.CloseCallBack.Close()
		}
	}()
	if opt.AuthKey != "" {
		go registerMachineWithAuthKey(PC, opt.AuthKey)
	}
}

func registerMachineWithAuthKey(PC pathControl, authKey string) {
	count := 0
	for count <= 5 {
		_, err := os.Stat(PC.Socket())
		if err != nil {
			count++
			time.Sleep(time.Second)
			continue
		}

		data, err := exec.Command(
			PC.Tailscale(),
			"--socket",
			PC.Socket(),
			"up",
			"--auth-key",
			authKey,
			"--timeout",
			"10s",
		).CombinedOutput()
		slog.Info("tailscale up", "output", string(data), "err", err)
		if err != nil {
			count++
			time.Sleep(time.Second)
			continue
		}

		break
	}
}
func Stop() {
	x := cmd
	cmd = nil
	if x != nil && x.Process != nil {
		slog.Info("stop tailscaled cmd")
		_ = x.Process.Signal(syscall.SIGTERM)
		go func() {
			time.Sleep(time.Second * 5)
			if x.Process != nil {
				_ = x.Process.Kill()
			}
		}()
	}
}

func rm(path ...string) {
	if len(path) == 0 {
		return
	}
	args := []string{"-rf"}
	args = append(args, path...)
	data, err := exec.Command("/system/bin/rm", args...).CombinedOutput()
	slog.Info("rm", "cmd", args, "output", string(data), "err", err)
}

func ln(src, dst string) {
	cmd := exec.Command("/system/bin/ln", "-s", src, dst)
	data, err := cmd.CombinedOutput()
	slog.Info("ln", "cmd", cmd.String(), "output", string(data), "err", err)
}

type pathControl struct {
	execPath   string
	statePath  string
	socketPath string
	execDir    string
	dataDir    string
}

func newPathControl(execPath, socketPath, statePath string) pathControl {
	return pathControl{
		execPath:   execPath,
		statePath:  statePath,
		socketPath: socketPath,
		execDir:    filepath.Dir(execPath),
		dataDir:    filepath.Dir(socketPath),
	}
}

func (p pathControl) TailscaledSo() string { return p.execPath }
func (p pathControl) Tailscaled() string   { return filepath.Join(p.dataDir, "tailscaled") }
func (p pathControl) TailscaleSo() string  { return filepath.Join(p.execDir, "libtailscale.so") }
func (p pathControl) Tailscale() string    { return filepath.Join(p.dataDir, "tailscale") }
func (p pathControl) DataDir(s ...string) string {
	if len(s) == 0 {
		return p.dataDir
	}
	return filepath.Join(append([]string{p.dataDir}, s...)...)
}
func (p pathControl) Socket() string { return p.socketPath }
func (p *pathControl) State() string { return p.statePath }

func tailscaledCmd(p pathControl, socks5host string) error {

	rm(p.Tailscale(), p.Tailscaled())
	// see: https://tailscale.com/kb/1207/small-tailscale
	ln(p.TailscaledSo(), p.Tailscale())
	ln(p.TailscaledSo(), p.Tailscaled())

	cmd = exec.Command(
		p.Tailscaled(),
		"--tun=userspace-networking",
		"--socks5-server="+socks5host,
		"--outbound-http-proxy-listen=:1057",
		fmt.Sprintf("--statedir=%s", p.State()),
		fmt.Sprintf("--socket=%s", p.Socket()),
	)
	cmd.Dir = p.DataDir()
	cmd.Env = []string{
		fmt.Sprintf("TS_LOGS_DIR=%s/logs", p.DataDir()),
	}

	errChan := make(chan error)
	defer close(errChan)

	go func() {
		stdOut, err := cmd.StdoutPipe()
		if err != nil {
			errChan <- err
			return
		}
		defer stdOut.Close()

		errChan <- nil

		s := bufio.NewScanner(stdOut)

		for s.Scan() {
			slog.Info(s.Text())
		}
	}()

	if err := <-errChan; err != nil {
		return err
	}

	go func() {
		stdOut, err := cmd.StderrPipe()
		if err != nil {
			errChan <- err
			return
		}
		defer stdOut.Close()

		errChan <- nil

		s := bufio.NewScanner(stdOut)

		for s.Scan() {
			slog.Info(s.Text())
		}
	}()

	if err := <-errChan; err != nil {
		return err
	}

	return cmd.Run()
}
