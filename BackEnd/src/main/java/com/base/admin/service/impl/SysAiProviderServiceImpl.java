package com.base.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.base.admin.domain.dto.SysAiProviderDTO;
import com.base.admin.domain.entity.SysAiProvider;
import com.base.admin.domain.vo.GeoAiProviderOptionVO;
import com.base.admin.domain.vo.SysAiProviderVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.mapper.SysAiProviderMapper;
import com.base.admin.service.SysAiProviderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SysAiProviderServiceImpl implements SysAiProviderService {

    private final SysAiProviderMapper providerMapper;

    @Override
    public List<SysAiProviderVO> listAll() {
        List<SysAiProvider> rows = providerMapper.selectList(new LambdaQueryWrapper<SysAiProvider>()
                .orderByAsc(SysAiProvider::getSortOrder)
                .orderByAsc(SysAiProvider::getId));
        return rows.stream().map(this::toVo).toList();
    }

    @Override
    public void update(SysAiProviderDTO dto) {
        SysAiProvider row = providerMapper.selectById(dto.getId());
        if (row == null) {
            throw new BusinessException("厂商配置不存在");
        }
        if (Boolean.TRUE.equals(dto.getClearApiKey())) {
            row.setApiKey("");
        } else if (StringUtils.hasText(dto.getApiKey()) && !isMaskedPlaceholder(dto.getApiKey())) {
            row.setApiKey(dto.getApiKey().trim());
        }
        row.setModel(dto.getModel().trim());
        row.setBaseUrl(StringUtils.hasText(dto.getBaseUrl()) ? dto.getBaseUrl().trim() : "");
        row.setEnabled(dto.getEnabled() != null && dto.getEnabled() == 1 ? 1 : 0);
        row.setRemark(dto.getRemark());
        providerMapper.updateById(row);
    }

    @Override
    public List<GeoAiProviderOptionVO> listReadyOptions() {
        List<GeoAiProviderOptionVO> list = new ArrayList<>();
        list.add(GeoAiProviderOptionVO.builder()
                .provider("local")
                .label("本地模板")
                .model("local-template")
                .available(true)
                .hint("不调用云端，随机模板扩写")
                .build());
        for (SysAiProvider row : listEnabledWithKey()) {
            list.add(GeoAiProviderOptionVO.builder()
                    .provider(row.getProvider())
                    .label(row.getProviderName())
                    .model(StringUtils.hasText(row.getModel()) ? row.getModel() : "")
                    .available(true)
                    .hint(StringUtils.hasText(row.getModel()) ? row.getModel() : "已配置 Key")
                    .build());
        }
        return list;
    }

    @Override
    public Optional<SysAiProvider> findReady(String provider) {
        if (!StringUtils.hasText(provider)) {
            return Optional.empty();
        }
        String p = provider.trim().toLowerCase(Locale.ROOT);
        if ("local".equals(p) || "heuristic".equals(p) || "template".equals(p)) {
            return Optional.empty();
        }
        return listEnabledWithKey().stream()
                .filter(r -> p.equalsIgnoreCase(r.getProvider()))
                .findFirst();
    }

    private List<SysAiProvider> listEnabledWithKey() {
        return providerMapper.selectList(new LambdaQueryWrapper<SysAiProvider>()
                        .eq(SysAiProvider::getEnabled, 1)
                        .orderByAsc(SysAiProvider::getSortOrder)
                        .orderByAsc(SysAiProvider::getId))
                .stream()
                .filter(r -> StringUtils.hasText(r.getApiKey()))
                .toList();
    }

    private SysAiProviderVO toVo(SysAiProvider row) {
        boolean hasKey = StringUtils.hasText(row.getApiKey());
        return SysAiProviderVO.builder()
                .id(row.getId())
                .provider(row.getProvider())
                .providerName(row.getProviderName())
                .apiKeyMasked(hasKey ? maskKey(row.getApiKey()) : "")
                .hasApiKey(hasKey)
                .model(row.getModel())
                .baseUrl(row.getBaseUrl())
                .enabled(row.getEnabled())
                .sortOrder(row.getSortOrder())
                .remark(row.getRemark())
                .build();
    }

    static String maskKey(String key) {
        String k = key.trim();
        if (k.length() <= 8) {
            return "****";
        }
        return k.substring(0, Math.min(4, k.length())) + "****" + k.substring(k.length() - 4);
    }

    private static boolean isMaskedPlaceholder(String value) {
        return value.contains("****");
    }
}
