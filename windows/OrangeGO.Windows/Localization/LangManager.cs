using System.Globalization;
using System.Windows;

namespace OrangeGO.Windows.Localization;

/// <summary>
/// 界面语言管理：按用户/系统选择把对应语言资源字典合并进 Application，
/// 控件用 DynamicResource 即时跟随；code-behind 用 T(key) 取当前语言文本。
/// 支持 15 种常用语言；"跟随系统"按系统区域匹配，未命中回退英文。
/// </summary>
public static class LangManager
{
    /// <summary>支持的界面语言代码（含中文）。</summary>
    public static readonly string[] Supported = ["en", "zh", "hi", "es", "fr", "ar", "bn", "ru", "pt", "id", "de", "ja", "ko", "it", "tr"];

    /// <summary>各语言在语言下拉中的显示名（母语自名）。按英文名称字母序排列（国际惯例/各大系统首选语言列表标准）。</summary>
    public static readonly (string Code, string Native)[] Menu =
    [
        ("ar", "العربية"),       // Arabic
        ("bn", "বাংলা"),         // Bengali
        ("zh", "简体中文"),       // Chinese (Simplified)
        ("en", "English"),       // English
        ("fr", "Français"),      // French
        ("de", "Deutsch"),       // German
        ("hi", "हिन्दी"),        // Hindi
        ("id", "Bahasa Indonesia"), // Indonesian
        ("it", "Italiano"),      // Italian
        ("ja", "日本語"),         // Japanese
        ("ko", "한국어"),         // Korean
        ("pt", "Português"),     // Portuguese
        ("ru", "Русский"),       // Russian
        ("es", "Español"),       // Spanish
        ("tr", "Türkçe"),        // Turkish
    ];

    private static ResourceDictionary? _dict;
    private static string _lang = "zh";

    /// <summary>当前生效语言代码（已解析，非 follow）。</summary>
    public static string Lang => _lang;

    /// <summary>语言切换后触发（用于刷新已生成文本）。</summary>
    public static event Action? LanguageChanged;

    /// <summary>应用指定语言；lang 为 "follow" 或空时按系统语言解析，未命中回退英文。</summary>
    public static void Apply(Application app, string lang)
    {
        var resolved = Resolve(lang);
        if (_dict is not null) app.Resources.MergedDictionaries.Remove(_dict);

        var uri = new Uri($"/Localization/{resolved}.xaml", UriKind.Relative);
        try { _dict = new ResourceDictionary { Source = uri }; }
        catch { _dict = new ResourceDictionary { Source = new Uri("/Localization/en.xaml", UriKind.Relative) }; resolved = "en"; }

        app.Resources.MergedDictionaries.Add(_dict);
        _lang = resolved;
        LanguageChanged?.Invoke();
    }

    /// <summary>取当前语言的文本；缺失时返回 key 本身。</summary>
    public static string T(string key)
        => _dict is not null && _dict[key] is string s ? s : key;

    /// <summary>解析语言代码：follow/空 → 系统语言；不在支持列表 → 英文。</summary>
    public static string Resolve(string? lang)
    {
        if (string.IsNullOrWhiteSpace(lang) || lang == "follow")
        {
            try
            {
                var c = CultureInfo.CurrentCulture.TwoLetterISOLanguageName.ToLowerInvariant();
                return Supported.Contains(c) ? c : "en";
            }
            catch { return "en"; }
        }
        return Supported.Contains(lang) ? lang : "en";
    }
}
