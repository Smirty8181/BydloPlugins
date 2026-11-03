package ru.bydlolend.bydlomod;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.bydlolend.bydlomod.adminlock.AdminLockGui;
import ru.bydlolend.bydlomod.adminlock.AdminLockService;
import ru.bydlolend.bydlomod.cmd.*;
import ru.bydlolend.bydlomod.frame.ShearsInvisibleFrameListener;
import ru.bydlolend.bydlomod.gui.MailboxGui;
import ru.bydlolend.bydlomod.gui.PlayerCardGui;
import ru.bydlolend.bydlomod.gui.ReportsGui;
import ru.bydlolend.bydlomod.gui.StaffGui;
import ru.bydlolend.bydlomod.gui.SupportGui;
import ru.bydlolend.bydlomod.gui.WarnsGui;
import ru.bydlolend.bydlomod.mail.LetterService;
import ru.bydlolend.bydlomod.mod.*;
import ru.bydlolend.bydlomod.spec.SpecService;
import ru.bydlolend.bydlomod.twitch.TwitchService;
import ru.bydlolend.bydlomod.uuid.UuidKeepListener;

public final class BydloMod extends JavaPlugin {
    private static BydloMod instance;
    private TwitchService twitch;
    private ModerationService moderation;
    private WarnService warns;
    private ReportService reports;
    private SupportService support;
    private HistoryService history;
    private MailService mail;
    private LetterService letters;
    private SpecService spec;
    private BlackMarkService blackMarks;
    private AdminLockService adminLock;
    private AdminLockGui adminLockGui;
    private ru.bydlolend.bydlomod.fx.CinematicStopService cinematic;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        twitch = new TwitchService(this);
        moderation = new ModerationService(this);
        history = new HistoryService(this);
        moderation.setHistory(history);
        mail = new MailService(this);
        letters = new LetterService(this);
        spec = new SpecService(this);
        blackMarks = new BlackMarkService(this);
        warns = new WarnService(this, moderation);
        warns.setBlackMarks(blackMarks);
        reports = new ReportService(this, mail);
        support = new SupportService(this, mail);
        adminLock = new AdminLockService(this);
        adminLockGui = new AdminLockGui(this, adminLock);
        cinematic = new ru.bydlolend.bydlomod.fx.CinematicStopService(this);

        WarnsGui warnsGui = new WarnsGui(this, warns);
        ReportsGui reportsGui = new ReportsGui(this, reports, warns);
        PlayerCardGui playerCardGui = new PlayerCardGui(this, warns, reports, moderation, history, blackMarks);
        reportsGui.setPlayerCard(playerCardGui);
        SupportGui supportGui = new SupportGui(support);
        MailboxGui mailboxGui = new MailboxGui(letters);
        StaffGui staffGui = new StaffGui(reports, support, reportsGui, supportGui, playerCardGui, spec);

        TwitchCommand twitchCmd = new TwitchCommand(twitch);
        getCommand("serverlock").setExecutor(new AdminLockCommand(adminLockGui));
        getCommand("twitch").setExecutor(twitchCmd);
        getCommand("twitch").setTabCompleter(twitchCmd);
        getCommand("twitchadmin").setExecutor(new TwitchAdminCommand(this));
        getCommand("vmute").setExecutor(new VmuteCommand(moderation));
        getCommand("unvmute").setExecutor(new UnvmuteCommand(moderation));
        getCommand("mute").setExecutor(new MuteCommand(moderation));
        getCommand("unmute").setExecutor(new UnmuteCommand(moderation));
        getCommand("warn").setExecutor(new WarnCommand(warns));
        getCommand("warns").setExecutor(new WarnsCommand(warns, warnsGui));
        getCommand("unwarn").setExecutor(new UnwarnCommand(warns));
        getCommand("report").setExecutor(new ReportCommand(reports));
        getCommand("reports").setExecutor(new ReportsCommand(reports, reportsGui));
        getCommand("playercard").setExecutor(new PlayerCardCommand(playerCardGui, warns));
        getCommand("mywarns").setExecutor(new MyWarnsCommand(warns, moderation, blackMarks));
        getCommand("rchat").setExecutor(new RChatCommand(reports));
        getCommand("support").setExecutor(new SupportCommand(support, supportGui));
        getCommand("schat").setExecutor(new SChatCommand(support));
        MailCommand mailCmd = new MailCommand(letters, mailboxGui);
        getCommand("mail").setExecutor(mailCmd);
        getCommand("mail").setTabCompleter(mailCmd);
        getCommand("staff").setExecutor(new StaffCommand(staffGui));
        SpecCommand specCmd = new SpecCommand(spec);
        getCommand("spec").setExecutor(specCmd);
        getCommand("spec").setTabCompleter(specCmd);

        var restartCmd = new ru.bydlolend.bydlomod.cmd.CinematicStopCommand(this,
                ru.bydlolend.bydlomod.fx.CinematicStopService.Mode.RESTART);
        var stopCmd = new ru.bydlolend.bydlomod.cmd.CinematicStopCommand(this,
                ru.bydlolend.bydlomod.fx.CinematicStopService.Mode.SHUTDOWN);
        var restart = getCommand("bydlorestart");
        if (restart != null) {
            restart.setExecutor(restartCmd);
            restart.setTabCompleter(restartCmd);
        }
        var shutdown = getCommand("bydloshutdown");
        if (shutdown != null) {
            shutdown.setExecutor(stopCmd);
            shutdown.setTabCompleter(stopCmd);
        }

        Bukkit.getPluginManager().registerEvents(moderation, this);
        Bukkit.getPluginManager().registerEvents(mail, this);
        Bukkit.getPluginManager().registerEvents(blackMarks, this);
        Bukkit.getPluginManager().registerEvents(new ChatMuteListener(this, moderation), this);
        Bukkit.getPluginManager().registerEvents(new ModCommandOverride(
                moderation, warns, reports, support, history, blackMarks, warnsGui, reportsGui,
                playerCardGui, supportGui, mailCmd, staffGui, spec), this);
        Bukkit.getPluginManager().registerEvents(adminLock, this);
        Bukkit.getPluginManager().registerEvents(adminLockGui, this);
        Bukkit.getPluginManager().registerEvents(warnsGui, this);
        Bukkit.getPluginManager().registerEvents(reportsGui, this);
        Bukkit.getPluginManager().registerEvents(playerCardGui, this);
        Bukkit.getPluginManager().registerEvents(supportGui, this);
        Bukkit.getPluginManager().registerEvents(new ShearsInvisibleFrameListener(), this);
        Bukkit.getPluginManager().registerEvents(letters, this);
        Bukkit.getPluginManager().registerEvents(mailboxGui, this);
        Bukkit.getPluginManager().registerEvents(staffGui, this);
        Bukkit.getPluginManager().registerEvents(spec, this);
        Bukkit.getPluginManager().registerEvents(new UuidKeepListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ru.bydlolend.bydlomod.fx.StopInterceptListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ru.bydlolend.bydlomod.fx.JoinFxListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ru.bydlolend.bydlomod.fx.WorldFxListener(), this);

        moderation.startExpiryTask();
        blackMarks.startTasks();
        adminLock.startExpiryTask();
        twitch.start();
        Bukkit.getScheduler().runTaskLater(this, () -> VoiceMuteHook.tryRegister(this), 20L);
        Bukkit.getScheduler().runTaskLater(this, () -> VoiceMuteHook.tryRegister(this), 100L);
        getLogger().info("BydloMod: mail/staff/spec + warns/reports/support/mute/vmute");
    }

    @Override
    public void onDisable() {
        if (twitch != null) twitch.stop();
        if (moderation != null) moderation.save();
        if (warns != null) warns.save();
        if (reports != null) reports.save();
        if (support != null) support.save();
        if (history != null) history.save();
        if (mail != null) mail.save();
        if (letters != null) letters.save();
        if (spec != null) spec.stopAll();
        if (blackMarks != null) blackMarks.save();
        if (adminLock != null) adminLock.save();
    }

    public static BydloMod get() { return instance; }
    public TwitchService twitch() { return twitch; }
    public ModerationService moderation() { return moderation; }
    public WarnService warns() { return warns; }
    public ReportService reports() { return reports; }
    public SupportService support() { return support; }
    public HistoryService history() { return history; }
    public BlackMarkService blackMarks() { return blackMarks; }
    public AdminLockService adminLock() { return adminLock; }
    public ru.bydlolend.bydlomod.fx.CinematicStopService cinematic() { return cinematic; }

    public String prefix() {
        return color(getConfig().getString("prefix", "&d[&5Быдло&d]&r") + " ");
    }

    public static String color(String s) {
        return s == null ? "" : s.replace('&', '§');
    }
}
