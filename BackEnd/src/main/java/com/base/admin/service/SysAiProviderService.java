package com.base.admin.service;

import com.base.admin.domain.dto.SysAiProviderDTO;
import com.base.admin.domain.entity.SysAiProvider;
import com.base.admin.domain.vo.GeoAiProviderOptionVO;
import com.base.admin.domain.vo.SysAiProviderVO;

import java.util.List;
import java.util.Optional;

public interface SysAiProviderService {

    List<SysAiProviderVO> listAll();

    void update(SysAiProviderDTO dto);

    /** 生成相似问题下拉：仅已配 Key 且启用的云厂商 */
    List<GeoAiProviderOptionVO> listReadyOptions();

    Optional<SysAiProvider> findReady(String provider);
}
