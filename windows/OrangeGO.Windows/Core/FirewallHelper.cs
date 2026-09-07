using System.Diagnostics;
using System.IO;

namespace OrangeGO.Windows.Core;

/// <summary>
/// 入站防火墙放行（best-effort）。Windows 默认阻止未请求入站流量，而 OrangeGO 是独立程序，
/// 与 LocalSend 共用 53317 端口却无自己的放行规则 → 手机发来的 TCP 连接在系统层被丢弃，
/// 表现为"电脑端收不到、无任何反应"（但 UDP 广播/mDNS 发现仍可互见在线）。
/// 这里按端口注册入站放行规则（对齐 LocalSend 策略）：已存在则跳过，缺失则提权添加一次，其余静默。
/// </summary>
public static class FirewallHelper
{
    private const string RuleNamePrefix = "OrangeGO";

    public static void EnsureInboundAllowed(int port)
    {
        try
        {
            if (RuleExists(port)) return;
            AddRuleElevated(port);
        }
        catch { /* 未提权/被拒绝等一律静默，不阻塞启动 */ }
    }

    /// <summary>用 netsh 查询规则是否存在（不弹 UAC）。</summary>
    private static bool RuleExists(int port)
    {
        try
        {
            var psi = new ProcessStartInfo
            {
                FileName = "netsh",
                Arguments = $"advfirewall firewall show rule name=\"{RuleName(port)}\"",
                RedirectStandardOutput = true,
                UseShellExecute = false,
                CreateNoWindow = true
            };
            using var p = Process.Start(psi)!;
            var text = p.StandardOutput.ReadToEnd();
            if (!p.WaitForExit(2000)) { p.Kill(); }
            return !text.Contains("No rules match", StringComparison.OrdinalIgnoreCase)
                && text.Contains(RuleNamePrefix, StringComparison.OrdinalIgnoreCase);
        }
        catch { return false; }
    }

    /// <summary>提权添加入站放行规则（仅缺失时触发一次 UAC；端口规则对 Debug/Release 任一构建生效）。</summary>
    private static void AddRuleElevated(int port)
    {
        var psi = new ProcessStartInfo
        {
            FileName = "netsh",
            Arguments = $"advfirewall firewall add rule name=\"{RuleName(port)}\" dir=in action=allow protocol=TCP localport={port} profile=private",
            Verb = "runas",
            UseShellExecute = true,
            CreateNoWindow = true,
            WindowStyle = ProcessWindowStyle.Hidden
        };
        using var p = Process.Start(psi);
        p?.WaitForExit(20000);
    }

    private static string RuleName(int port) => $"{RuleNamePrefix} {port}";
}