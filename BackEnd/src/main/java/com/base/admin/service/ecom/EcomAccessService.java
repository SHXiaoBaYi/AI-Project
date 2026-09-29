package com.base.admin.service.ecom;

import com.base.admin.exception.BusinessException;
import com.base.admin.security.LoginUser;
import com.base.admin.util.LocalhostAccess;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.*;

@Service
@RequiredArgsConstructor
public class EcomAccessService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public void assertAccess() {
        if (!currentUserCanAccess()) {
            throw new BusinessException("无权访问电商运营模块");
        }
    }

    public void assertCanManageAcl() {
        if (!canManageAcl()) {
            throw new BusinessException("仅 Bella 可管理电商授权");
        }
    }

    public boolean currentUserCanAccess() {
        LoginUser user = currentUser();
        if (user == null || user.getUsername() == null) {
            return false;
        }
        if (isOperator()) {
            return true;
        }
        Scope scope = loadAclScope(user.getUserId());
        return scope != null && scope.enabled;
    }

    public boolean canManageAcl() {
        return isOperator();
    }

    public boolean canReturnMain() {
        return isOperator();
    }

    public boolean isOperator() {
        LoginUser user = currentUser();
        if (user == null) {
            return false;
        }
        return LocalhostAccess.isLocalhostRequest(currentRequest())
                || LocalhostAccess.isDataScopeOperator(user.getUsername());
    }

    public Scope currentScope() {
        if (isOperator()) {
            return Scope.full();
        }
        LoginUser user = currentUser();
        if (user == null) {
            return Scope.none();
        }
        Scope scope = loadAclScope(user.getUserId());
        return scope == null || !scope.enabled ? Scope.none() : scope;
    }

    public void assertPlatformAccess(String platform) {
        Scope scope = currentScope();
        if (scope.fullAccess) {
            return;
        }
        if (!scope.enabled) {
            throw new BusinessException("无权访问电商运营模块");
        }
        if (scope.allPlatforms) {
            return;
        }
        if (!StringUtils.hasText(platform) || !scope.platforms.contains(platform.trim().toLowerCase(Locale.ROOT))) {
            throw new BusinessException("无权访问该平台数据：" + platform);
        }
    }

    public void assertShopAccess(Long shopId) {
        Scope scope = currentScope();
        if (scope.fullAccess) {
            return;
        }
        if (!scope.enabled) {
            throw new BusinessException("无权访问电商运营模块");
        }
        if (shopId == null) {
            throw new BusinessException("店铺不能为空");
        }
        Map<String, Object> shop = jdbc.query("""
                SELECT id, platform FROM ecom_shop WHERE id = ? AND is_active = 1 LIMIT 1
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            Map<String, Object> m = new HashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("platform", rs.getString("platform"));
            return m;
        }, shopId);
        if (shop == null) {
            throw new BusinessException("店铺不存在");
        }
        String platform = String.valueOf(shop.get("platform"));
        assertPlatformAccess(platform);
        if (scope.allShops) {
            return;
        }
        if (!scope.shopIds.contains(shopId)) {
            throw new BusinessException("无权访问该店铺数据");
        }
    }

    /** 追加平台/店铺切片条件；事实/看板等表用 shop_id 列 */
    public void appendScopeFilter(StringBuilder where, List<Object> args, String alias) {
        appendScopeFilter(where, args, alias, "shop_id");
    }

    /**
     * @param shopIdColumn 店铺列名：事实表为 shop_id，ecom_shop 自身为 id
     */
    public void appendScopeFilter(StringBuilder where, List<Object> args, String alias, String shopIdColumn) {
        Scope scope = currentScope();
        if (scope.fullAccess) {
            return;
        }
        if (!scope.enabled) {
            where.append(" AND 1=0 ");
            return;
        }
        String p = alias + ".platform";
        String s = alias + "." + (StringUtils.hasText(shopIdColumn) ? shopIdColumn : "shop_id");
        if (!scope.allPlatforms) {
            if (scope.platforms.isEmpty()) {
                where.append(" AND 1=0 ");
                return;
            }
            where.append(" AND ").append(p).append(" IN (");
            where.append(String.join(",", Collections.nCopies(scope.platforms.size(), "?")));
            where.append(") ");
            args.addAll(scope.platforms);
        }
        if (!scope.allShops) {
            if (scope.shopIds.isEmpty()) {
                where.append(" AND 1=0 ");
                return;
            }
            where.append(" AND ").append(s).append(" IN (");
            where.append(String.join(",", Collections.nCopies(scope.shopIds.size(), "?")));
            where.append(") ");
            args.addAll(scope.shopIds);
        }
    }

    public Map<String, Object> scopeAsMap() {
        Scope scope = currentScope();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fullAccess", scope.fullAccess);
        m.put("enabled", scope.enabled);
        m.put("allPlatforms", scope.allPlatforms);
        m.put("allShops", scope.allShops);
        m.put("platforms", new ArrayList<>(scope.platforms));
        m.put("shopIds", new ArrayList<>(scope.shopIds));
        return m;
    }

    public String currentUsername() {
        LoginUser user = currentUser();
        return user == null ? "" : user.getUsername();
    }

    public Long currentUserId() {
        LoginUser user = currentUser();
        return user == null ? null : user.getUserId();
    }

    private Scope loadAclScope(Long userId) {
        if (userId == null) {
            return null;
        }
        return jdbc.query("""
                SELECT enabled, all_platforms, all_shops, platforms_json, shop_ids_json
                FROM ecom_acl WHERE user_id = ? AND is_active = 1 LIMIT 1
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            Scope scope = new Scope();
            scope.enabled = rs.getInt("enabled") == 1;
            scope.allPlatforms = rs.getInt("all_platforms") == 1;
            scope.allShops = rs.getInt("all_shops") == 1;
            scope.platforms = parsePlatforms(rs.getString("platforms_json"));
            scope.shopIds = parseShopIds(rs.getString("shop_ids_json"));
            // 兼容旧数据：未配切片时视为全量
            if (!scope.allPlatforms && scope.platforms.isEmpty() && !scope.allShops && scope.shopIds.isEmpty()) {
                scope.allPlatforms = true;
                scope.allShops = true;
            }
            return scope;
        }, userId);
    }

    private Set<String> parsePlatforms(String json) {
        Set<String> set = new LinkedHashSet<>();
        if (!StringUtils.hasText(json)) {
            return set;
        }
        try {
            List<String> list = objectMapper.readValue(json, new TypeReference<>() {});
            for (String p : list) {
                if (StringUtils.hasText(p)) {
                    set.add(p.trim().toLowerCase(Locale.ROOT));
                }
            }
        } catch (Exception ignored) {
        }
        return set;
    }

    private Set<Long> parseShopIds(String json) {
        Set<Long> set = new LinkedHashSet<>();
        if (!StringUtils.hasText(json)) {
            return set;
        }
        try {
            List<Long> list = objectMapper.readValue(json, new TypeReference<>() {});
            for (Long id : list) {
                if (id != null) {
                    set.add(id);
                }
            }
        } catch (Exception ignored) {
        }
        return set;
    }

    private LoginUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser loginUser)) {
            return null;
        }
        return loginUser;
    }

    private HttpServletRequest currentRequest() {
        var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest();
    }

    public static final class Scope {
        public boolean fullAccess;
        public boolean enabled;
        public boolean allPlatforms;
        public boolean allShops;
        public Set<String> platforms = new LinkedHashSet<>();
        public Set<Long> shopIds = new LinkedHashSet<>();

        static Scope full() {
            Scope s = new Scope();
            s.fullAccess = true;
            s.enabled = true;
            s.allPlatforms = true;
            s.allShops = true;
            return s;
        }

        static Scope none() {
            Scope s = new Scope();
            s.enabled = false;
            return s;
        }
    }
}
