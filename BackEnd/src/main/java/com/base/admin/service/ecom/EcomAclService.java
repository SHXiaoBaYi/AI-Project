package com.base.admin.service.ecom;

import com.base.admin.domain.dto.EcomAclSaveDTO;
import com.base.admin.exception.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;

@Service
@RequiredArgsConstructor
public class EcomAclService {

    private final JdbcTemplate jdbc;
    private final EcomAccessService accessService;
    private final ObjectMapper objectMapper;

    public List<Map<String, Object>> list() {
        accessService.assertCanManageAcl();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT a.user_id, u.username, u.nickname, a.enabled, a.all_platforms, a.all_shops,
                       a.platforms_json, a.shop_ids_json, a.remark, a.grant_by, a.grant_time, a.update_time
                FROM ecom_acl a
                LEFT JOIN sys_user u ON u.user_id = a.user_id
                WHERE a.is_active = 1
                ORDER BY a.grant_time DESC
                """);
        for (Map<String, Object> row : rows) {
            row.put("platforms", parseStringList(row.get("platforms_json")));
            row.put("shopIds", parseLongList(row.get("shop_ids_json")));
            row.remove("platforms_json");
            row.remove("shop_ids_json");
            enrichShopNames(row);
        }
        return rows;
    }

    public List<Map<String, Object>> candidateUsers(String keyword) {
        accessService.assertCanManageAcl();
        if (StringUtils.hasText(keyword)) {
            String kw = "%" + keyword.trim() + "%";
            return jdbc.queryForList("""
                    SELECT user_id, username, nickname FROM sys_user
                    WHERE is_active = 1 AND (username LIKE ? OR nickname LIKE ?)
                    ORDER BY user_id
                    """, kw, kw);
        }
        return jdbc.queryForList("""
                SELECT user_id, username, nickname FROM sys_user
                WHERE is_active = 1 ORDER BY user_id
                """);
    }

    @Transactional
    public void save(EcomAclSaveDTO dto) {
        accessService.assertCanManageAcl();
        if (dto == null || dto.getUserId() == null) {
            throw new BusinessException("userId 不能为空");
        }
        int enabled = Boolean.TRUE.equals(dto.getEnabled()) ? 1 : 0;
        boolean allPlatforms = Boolean.TRUE.equals(dto.getAllPlatforms());
        boolean allShops = Boolean.TRUE.equals(dto.getAllShops());
        List<String> platforms = normalizePlatforms(dto.getPlatforms());
        List<Long> shopIds = normalizeShopIds(dto.getShopIds());

        if (enabled == 1) {
            if (!allPlatforms && platforms.isEmpty()) {
                throw new BusinessException("请选择平台切片，或勾选全部平台");
            }
            if (!allShops && shopIds.isEmpty()) {
                throw new BusinessException("请选择店铺切片，或勾选全部店铺");
            }
            if (!allShops) {
                validateShopsBelongToPlatforms(shopIds, allPlatforms, platforms);
            }
        }

        String remark = dto.getRemark() == null ? "" : dto.getRemark().trim();
        String by = accessService.currentUsername();
        String platformsJson = toJson(platforms);
        String shopIdsJson = toJson(shopIds);
        jdbc.update("""
                INSERT INTO ecom_acl
                  (user_id, enabled, all_platforms, all_shops, platforms_json, shop_ids_json, remark, grant_by, is_active)
                VALUES (?,?,?,?,?,?,?,?,1)
                ON DUPLICATE KEY UPDATE
                  enabled = VALUES(enabled),
                  all_platforms = VALUES(all_platforms),
                  all_shops = VALUES(all_shops),
                  platforms_json = VALUES(platforms_json),
                  shop_ids_json = VALUES(shop_ids_json),
                  remark = VALUES(remark),
                  grant_by = VALUES(grant_by),
                  grant_time = IF(VALUES(enabled)=1, CURRENT_TIMESTAMP, grant_time),
                  is_active = 1
                """, dto.getUserId(), enabled, allPlatforms ? 1 : 0, allShops ? 1 : 0,
                platformsJson, shopIdsJson, remark, by);
    }

    @Transactional
    public void remove(Long userId) {
        accessService.assertCanManageAcl();
        if (userId == null) {
            throw new BusinessException("userId 不能为空");
        }
        jdbc.update("UPDATE ecom_acl SET enabled = 0, is_active = 0 WHERE user_id = ?", userId);
    }

    private void validateShopsBelongToPlatforms(List<Long> shopIds, boolean allPlatforms, List<String> platforms) {
        if (shopIds.isEmpty()) {
            return;
        }
        String in = String.join(",", Collections.nCopies(shopIds.size(), "?"));
        List<Map<String, Object>> shops = jdbc.queryForList(
                "SELECT id, platform FROM ecom_shop WHERE is_active = 1 AND id IN (" + in + ")",
                shopIds.toArray());
        if (shops.size() != shopIds.size()) {
            throw new BusinessException("存在无效店铺ID");
        }
        if (allPlatforms) {
            return;
        }
        Set<String> allowed = new HashSet<>(platforms);
        for (Map<String, Object> shop : shops) {
            String p = String.valueOf(shop.get("platform")).toLowerCase(Locale.ROOT);
            if (!allowed.contains(p)) {
                throw new BusinessException("店铺不属于所选平台切片：" + shop.get("id"));
            }
        }
    }

    private void enrichShopNames(Map<String, Object> row) {
        @SuppressWarnings("unchecked")
        List<Long> shopIds = (List<Long>) row.get("shopIds");
        if (shopIds == null || shopIds.isEmpty()) {
            row.put("shopNames", List.of());
            return;
        }
        String in = String.join(",", Collections.nCopies(shopIds.size(), "?"));
        List<String> names = jdbc.queryForList(
                "SELECT CONCAT(platform, ':', COALESCE(NULLIF(shop_name,''), shop_code)) FROM ecom_shop WHERE id IN (" + in + ")",
                String.class, shopIds.toArray());
        row.put("shopNames", names);
    }

    private List<String> normalizePlatforms(List<String> raw) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (raw == null) {
            return new ArrayList<>();
        }
        for (String p : raw) {
            if (StringUtils.hasText(p)) {
                set.add(p.trim().toLowerCase(Locale.ROOT));
            }
        }
        return new ArrayList<>(set);
    }

    private List<Long> normalizeShopIds(List<Long> raw) {
        LinkedHashSet<Long> set = new LinkedHashSet<>();
        if (raw == null) {
            return new ArrayList<>();
        }
        for (Long id : raw) {
            if (id != null) {
                set.add(id);
            }
        }
        return new ArrayList<>(set);
    }

    private List<String> parseStringList(Object json) {
        if (json == null || !StringUtils.hasText(String.valueOf(json))) {
            return List.of();
        }
        try {
            return objectMapper.readValue(String.valueOf(json), new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<Long> parseLongList(Object json) {
        if (json == null || !StringUtils.hasText(String.valueOf(json))) {
            return List.of();
        }
        try {
            return objectMapper.readValue(String.valueOf(json), new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o == null ? List.of() : o);
        } catch (Exception e) {
            return "[]";
        }
    }
}
