package com.base.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.base.admin.config.GeoBrowserProbeProperties;
import com.base.admin.domain.entity.GeoPlatformAccount;
import com.base.admin.domain.vo.GeoBrowserAccountVO;
import com.base.admin.mapper.GeoPlatformAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 浏览器自动监测的「平台账号」数据面：只读 geo_platform_account，不碰浏览器。
 *
 * <p>浏览器操作统一由 {@link GeoBrowserProbeService} 的单线程执行，本服务负责：
 * 选账号、算登录态文件路径、判断缓存是否过期、回写登录结果、输出就绪状态。
 * 密码只在本进程内用于填表，绝不写入日志 / 诊断 / VO。
 */
@Service
@RequiredArgsConstructor
public class GeoBrowserAccountService {

    public static final String LOGIN_PASSWORD = "PASSWORD";
    public static final String LOGIN_STORAGE_STATE = "STORAGE_STATE";
    /** 登录态留在某台 Windows 的 Chrome 用户目录里，不上传到服务器 */
    public static final String LOGIN_PROFILE = "PROFILE";

    public static final String STATE_NONE = "NONE";
    public static final String STATE_READY = "READY";
    public static final String STATE_FAILED = "FAILED";
    public static final String STATE_EXPIRED = "EXPIRED";

    /** 连续失败达到该次数后不再进入候选，直到人工重新登录把计数清零 */
    public static final int MAX_CONSECUTIVE_FAILS = 3;

    private final GeoPlatformAccountMapper accountMapper;
    private final GeoBrowserProbeProperties properties;

    /**
     * 一次执行抽一个登录态：在可用候选里取最久未使用的若干个，再随机一个。
     * 排除本轮已经失效的账号。没有候选时返回空，调用方不要再回退到共用的「平台名.json」。
     */
    public Optional<GeoPlatformAccount> pickForRun(String platform, Set<Long> excludeIds) {
        if (!StringUtils.hasText(platform)) {
            return Optional.empty();
        }
        List<GeoPlatformAccount> eligible = new ArrayList<>();
        for (GeoPlatformAccount row : listEnabled(platform.trim())) {
            if (excludeIds != null && excludeIds.contains(row.getId())) {
                continue;
            }
            if (isPoolCandidate(row)) {
                eligible.add(row);
            }
        }
        if (eligible.isEmpty()) {
            return Optional.empty();
        }
        eligible.sort(Comparator
                .comparing(GeoPlatformAccount::getBrowserLastUsedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(GeoPlatformAccount::getId));
        int window = Math.min(3, eligible.size());
        return Optional.of(eligible.get(ThreadLocalRandom.current().nextInt(window)));
    }

    /** 是否还能被抽中。人工登录态失效后必须重登；密码号的缓存过期仍可重新登录。 */
    public boolean isPoolCandidate(GeoPlatformAccount account) {
        if (account == null || account.getBrowserEnabled() == null || account.getBrowserEnabled() != 1) {
            return false;
        }
        if (!"正常".equals(account.getAccountStatus())) {
            return false;
        }
        int fails = account.getBrowserLoginFailCount() == null ? 0 : account.getBrowserLoginFailCount();
        if (fails >= MAX_CONSECUTIVE_FAILS || STATE_FAILED.equals(account.getBrowserLoginStatus())) {
            return false;
        }
        String type = StringUtils.hasText(account.getBrowserLoginType()) ? account.getBrowserLoginType() : LOGIN_PASSWORD;
        if (LOGIN_PROFILE.equalsIgnoreCase(type)) {
            return false;
        }
        if (LOGIN_STORAGE_STATE.equalsIgnoreCase(type)) {
            if (STATE_EXPIRED.equals(account.getBrowserLoginStatus())) {
                return false;
            }
            return stateFileExists(account);
        }
        return StringUtils.hasText(account.getPassword());
    }

    private List<GeoPlatformAccount> listEnabled(String platform) {
        return accountMapper.selectList(new LambdaQueryWrapper<GeoPlatformAccount>()
                .eq(GeoPlatformAccount::getPlatformName, platform)
                .eq(GeoPlatformAccount::getBrowserEnabled, 1)
                .eq(GeoPlatformAccount::getAccountStatus, "正常")
                .orderByAsc(GeoPlatformAccount::getId));
    }

    public GeoPlatformAccount require(Long accountId) {
        GeoPlatformAccount row = accountMapper.selectById(accountId);
        if (row == null) {
            throw new com.base.admin.exception.BusinessException("平台账号不存在");
        }
        return row;
    }

    /** 所有启用了浏览器监测的账号（供就绪状态列表） */
    public List<GeoPlatformAccount> listBrowserAccounts() {
        return accountMapper.selectList(new LambdaQueryWrapper<GeoPlatformAccount>()
                .eq(GeoPlatformAccount::getBrowserEnabled, 1)
                .orderByAsc(GeoPlatformAccount::getPlatformName)
                .orderByAsc(GeoPlatformAccount::getSortOrder));
    }

    public Path stateDir() {
        String dir = StringUtils.hasText(properties.getStorageStateDir())
                ? properties.getStorageStateDir() : "./uploads/geo/auto-monitor/state";
        return Path.of(dir).toAbsolutePath().normalize();
    }

    /** 账号专属登录态文件（自动登录导出用）；即使文件不存在也返回目标路径 */
    public Path accountStateFile(GeoPlatformAccount account) {
        String relative = StringUtils.hasText(account.getBrowserStatePath())
                ? account.getBrowserStatePath() : "account-" + account.getId() + ".json";
        return stateDir().resolve(safeFileName(relative)).normalize();
    }

    /** 手工上传的兜底登录态：{平台名}.json（兼容上一版「采集诊断」上传） */
    public Path manualStateFile(String platform) {
        Path file = stateDir().resolve(safeFileName(platform.trim() + ".json")).normalize();
        return Files.isRegularFile(file) ? file : null;
    }

    public boolean stateFileExists(GeoPlatformAccount account) {
        return Files.isRegularFile(accountStateFile(account));
    }

    /** 登录态是否过期（无成功时间视为过期） */
    public boolean isExpired(GeoPlatformAccount account) {
        LocalDateTime at = account.getBrowserLoginAt();
        if (at == null) {
            return true;
        }
        long ttl = properties.getStateTtlMs();
        return ttl > 0 && System.currentTimeMillis() - toMillis(at) > ttl;
    }

    public void markReady(GeoPlatformAccount account, Path stateFile) {
        String relative = stateDir().relativize(stateFile.toAbsolutePath().normalize()).toString().replace('\\', '/');
        accountMapper.update(null, new LambdaUpdateWrapper<GeoPlatformAccount>()
                .eq(GeoPlatformAccount::getId, account.getId())
                .set(GeoPlatformAccount::getBrowserLoginStatus, STATE_READY)
                .set(GeoPlatformAccount::getBrowserStatePath, relative)
                .set(GeoPlatformAccount::getBrowserLoginAt, LocalDateTime.now())
                .set(GeoPlatformAccount::getBrowserLoginError, "")
                .set(GeoPlatformAccount::getBrowserLoginFailCount, 0)
                .set(GeoPlatformAccount::getLastLoginTime, LocalDateTime.now()));
        account.setBrowserLoginStatus(STATE_READY);
        account.setBrowserStatePath(relative);
        account.setBrowserLoginAt(LocalDateTime.now());
        account.setBrowserLoginError("");
        account.setBrowserLoginFailCount(0);
    }

    public void markFailed(GeoPlatformAccount account, String error) {
        int fails = (account.getBrowserLoginFailCount() == null ? 0 : account.getBrowserLoginFailCount()) + 1;
        String message = error == null ? "" : (error.length() > 900 ? error.substring(0, 900) : error);
        accountMapper.update(null, new LambdaUpdateWrapper<GeoPlatformAccount>()
                .eq(GeoPlatformAccount::getId, account.getId())
                .set(GeoPlatformAccount::getBrowserLoginStatus, STATE_FAILED)
                .set(GeoPlatformAccount::getBrowserLoginError, message)
                .set(GeoPlatformAccount::getBrowserLoginFailCount, fails));
        account.setBrowserLoginStatus(STATE_FAILED);
        account.setBrowserLoginError(message);
        account.setBrowserLoginFailCount(fails);
    }

    public void markExpired(GeoPlatformAccount account) {
        invalidateSession(account, "");
    }

    /** 密码号的缓存失效：状态改为过期，账号仍留在候选里，下次用密码重新登录。 */
    public void invalidateSession(GeoPlatformAccount account, String reason) {
        String message = clip(reason);
        accountMapper.update(null, new LambdaUpdateWrapper<GeoPlatformAccount>()
                .eq(GeoPlatformAccount::getId, account.getId())
                .set(GeoPlatformAccount::getBrowserLoginStatus, STATE_EXPIRED)
                .set(GeoPlatformAccount::getBrowserLoginError, message));
        account.setBrowserLoginStatus(STATE_EXPIRED);
        account.setBrowserLoginError(message);
    }

    /** 登录墙或人工登录态失效：移出候选，直到在平台账号管理里重新登录。 */
    public void quarantine(GeoPlatformAccount account, String reason) {
        int fails = (account.getBrowserLoginFailCount() == null ? 0 : account.getBrowserLoginFailCount()) + 1;
        String message = clip(reason);
        accountMapper.update(null, new LambdaUpdateWrapper<GeoPlatformAccount>()
                .eq(GeoPlatformAccount::getId, account.getId())
                .set(GeoPlatformAccount::getBrowserLoginStatus, STATE_EXPIRED)
                .set(GeoPlatformAccount::getBrowserLoginError, message)
                .set(GeoPlatformAccount::getBrowserLoginFailCount, fails));
        account.setBrowserLoginStatus(STATE_EXPIRED);
        account.setBrowserLoginError(message);
        account.setBrowserLoginFailCount(fails);
    }

    /** 记录这次执行抽中了该登录态，供下一轮优先换一个更久没用的。 */
    public void touchUsed(Long accountId) {
        if (accountId == null) {
            return;
        }
        accountMapper.update(null, new LambdaUpdateWrapper<GeoPlatformAccount>()
                .eq(GeoPlatformAccount::getId, accountId)
                .set(GeoPlatformAccount::getBrowserLastUsedAt, LocalDateTime.now()));
    }

    /** 密码号：READY 且未超过缓存时间。人工登录态和本机 profile：READY 即可，不按 12 小时作废。 */
    public boolean isReady(GeoPlatformAccount account) {
        if (account == null || !STATE_READY.equals(account.getBrowserLoginStatus())) {
            return false;
        }
        String type = StringUtils.hasText(account.getBrowserLoginType()) ? account.getBrowserLoginType() : LOGIN_PASSWORD;
        if (LOGIN_PROFILE.equalsIgnoreCase(type)) {
            return true;
        }
        if (!stateFileExists(account)) {
            return false;
        }
        if (LOGIN_STORAGE_STATE.equalsIgnoreCase(type)) {
            return true;
        }
        return !isExpired(account);
    }

    /** 本机窗口登录成功。不写 storageState 文件。 */
    public void markProfileReady(GeoPlatformAccount account) {
        accountMapper.update(null, new LambdaUpdateWrapper<GeoPlatformAccount>()
                .eq(GeoPlatformAccount::getId, account.getId())
                .set(GeoPlatformAccount::getBrowserLoginStatus, STATE_READY)
                .set(GeoPlatformAccount::getBrowserLoginAt, LocalDateTime.now())
                .set(GeoPlatformAccount::getBrowserLoginError, "")
                .set(GeoPlatformAccount::getBrowserLoginFailCount, 0)
                .set(GeoPlatformAccount::getLastLoginTime, LocalDateTime.now()));
        account.setBrowserLoginStatus(STATE_READY);
        account.setBrowserLoginError("");
        account.setBrowserLoginFailCount(0);
    }

    private static String clip(String reason) {
        if (reason == null) {
            return "";
        }
        return reason.length() > 900 ? reason.substring(0, 900) : reason;
    }

    public List<GeoBrowserAccountVO> statusList() {
        List<GeoBrowserAccountVO> result = new ArrayList<>();
        for (GeoPlatformAccount row : listBrowserAccounts()) {
            GeoBrowserAccountVO vo = new GeoBrowserAccountVO();
            vo.setPlatform(row.getPlatformName());
            vo.setAccountId(row.getId());
            vo.setAccount(mask(row.getAccount()));
            vo.setHasPassword(StringUtils.hasText(row.getPassword()));
            vo.setLoginType(StringUtils.hasText(row.getBrowserLoginType()) ? row.getBrowserLoginType() : LOGIN_PASSWORD);
            vo.setLoginStatus(StringUtils.hasText(row.getBrowserLoginStatus()) ? row.getBrowserLoginStatus() : STATE_NONE);
            vo.setStatePath(row.getBrowserStatePath());
            vo.setLoginAt(row.getBrowserLoginAt());
            vo.setError(row.getBrowserLoginError());
            vo.setFailCount(row.getBrowserLoginFailCount());
            vo.setReady(isReady(row));
            result.add(vo);
        }
        return result;
    }

    /** 账号脱敏：保留前 2 位和后 2 位 */
    public String mask(String account) {
        if (!StringUtils.hasText(account)) {
            return "";
        }
        String s = account.trim();
        if (s.length() <= 4) {
            return "**";
        }
        return s.substring(0, 2) + "****" + s.substring(s.length() - 2);
    }

    private static String safeFileName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static long toMillis(LocalDateTime time) {
        return time.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
