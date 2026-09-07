using System.Net;
using OrangeGO.Windows.Core;

// 本机自测：ReceiveServer(环回) 接收，SenderClient 发送，验证 init→file 闭环。
var errorCount = 0;

var tmp = Path.Combine(Path.GetTempPath(), "orangoGO_selftest");
Directory.CreateDirectory(tmp);
var src = Path.Combine(tmp, "hello_测试.bin");
var content = new byte[2 * 1024 * 1024];
new Random(42).NextBytes(content);
await File.WriteAllBytesAsync(src, content);

var identity = () => new Protocol.DeviceInfo { DeviceId = "og_test", Name = "self" };
var savedDir = Path.Combine(tmp, "saved");
Directory.CreateDirectory(savedDir);

using (var server = new ReceiveServer { OnIncoming = s => Task.FromResult<string?>(savedDir) })
using (var sender = new SenderClient(identity))
{
    server.Start(); // 固定监听 Protocol.Port（53317），与正式版一致
    try
    {
        var sendId = Guid.NewGuid().ToString("N");
        var token = await sender.InitAsync(IPAddress.Loopback, Protocol.Port, sendId, new[] { src });
        await sender.SendFileAsync(IPAddress.Loopback, Protocol.Port, token, src, p => { });
        await sender.CancelAsync(IPAddress.Loopback, Protocol.Port, sendId);

        var dest = Path.Combine(savedDir, "hello_测试.bin");
        if (!File.Exists(dest)) { Console.WriteLine("[FAIL] 未生成接收文件"); errorCount++; }
        else if (File.ReadAllBytes(dest).SequenceEqual(content)) Console.WriteLine("[OK] 文件字节一致 (" + content.Length + " B)");
        else { Console.WriteLine("[FAIL] 文件内容不一致"); errorCount++; }
    }
    catch (Exception ex)
    {
        Console.WriteLine($"[FAIL] 异常：{ex.Message}");
        errorCount++;
    }
}

Directory.Delete(tmp, true);
Console.WriteLine(errorCount == 0 ? "\n自测全部通过 ✔" : $"\n自测存在 {errorCount} 处失败 ✘");

// —— mDNS 互发现检验（同一网卡多播下两实例应互相看见）——
errorCount += await ProbeMdnsAsync();
Console.WriteLine(errorCount == 0 ? "含 mDNS 全部通过 ✔" : $"\n含 mDNS 仍存在 {errorCount} 处失败 ✘");
return errorCount == 0 ? 0 : 1;

static async Task<int> ProbeMdnsAsync()
{
    try
    {
        using var a = new MdnsDiscovery("Selftest-A", "og_aaa");
        using var b = new MdnsDiscovery("Selftest-B", "og_bbb");
        var aSaw = new TaskCompletionSource<bool>();
        var bSaw = new TaskCompletionSource<bool>();
        a.PeersChanged += peers => { if (peers.Any(p => p.DeviceId == "og_bbb")) aSaw.TrySetResult(true); };
        b.PeersChanged += peers => { if (peers.Any(p => p.DeviceId == "og_aaa")) bSaw.TrySetResult(true); };

        var t = Task.WhenAll(aSaw.Task, bSaw.Task).WaitAsync(TimeSpan.FromSeconds(20));
        try { await t; Console.WriteLine("[OK] mDNS 互发现：A↔B 均互相看见"); return 0; }
        catch (TimeoutException)
        {
            Console.WriteLine("[FAIL] mDNS 互发现超时（可能受多播/防火墙限制）");
            return 1;
        }
    }
    catch (Exception ex)
    {
        Console.WriteLine($"[FAIL] mDNS 异常：{ex.Message}");
        return 1;
    }
}