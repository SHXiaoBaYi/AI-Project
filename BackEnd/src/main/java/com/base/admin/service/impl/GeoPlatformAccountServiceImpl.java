package com.base.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.GeoPlatformAccountDTO;
import com.base.admin.domain.dto.GeoPlatformAccountQueryDTO;
import com.base.admin.domain.entity.GeoPlatform;
import com.base.admin.domain.entity.GeoPlatformAccount;
import com.base.admin.domain.entity.SysUser;
import com.base.admin.domain.vo.GeoPlatformAccountListVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.mapper.GeoPlatformAccountMapper;
import com.base.admin.mapper.SysUserMapper;
import com.base.admin.service.GeoPlatformAccountService;
import com.base.admin.service.GeoPlatformService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GeoPlatformAccountServiceImpl implements GeoPlatformAccountService {

    private static final String STATUS_NORMAL = "正常";
    private static final String STATUS_DISABLED = "停用";
    private static final String STATUS_EXPIRED = "过期";

    private final GeoPlatformAccountMapper accountMapper;
    private final GeoPlatformService platformService;
    private final SysUserMapper userMapper;

    @Override
    public PageResult<GeoPlatformAccountListVO> list(GeoPlatformAccountQueryDTO query) {
        LambdaQueryWrapper<GeoPlatformAccount> wrapper = new LambdaQueryWrapper<GeoPlatformAccount>()
                .eq(query.getPlatformId() != null, GeoPlatformAccount::getPlatformId, query.getPlatformId())
                .like(StringUtils.hasText(query.getAccount()), GeoPlatformAccount::getAccount, query.getAccount())
                .eq(query.getManagerUserId() != null, GeoPlatformAccount::getManagerUserId, query.getManagerUserId())
                .eq(query.getHolderUserId() != null, GeoPlatformAccount::getHolderUserId, query.getHolderUserId())
                .eq(query.getOpenerUserId() != null, GeoPlatformAccount::getOpenerUserId, query.getOpenerUserId())
                .eq(query.getRecharged() != null, GeoPlatformAccount::getRecharged, query.getRecharged())
                .eq(query.getVerified() != null, GeoPlatformAccount::getVerified, query.getVerified())
                .eq(StringUtils.hasText(query.getAccountStatus()), GeoPlatformAccount::getAccountStatus, query.getAccountStatus())
                .eq(StringUtils.hasText(query.getLoginMethod()), GeoPlatformAccount::getLoginMethod, query.getLoginMethod())
                .orderByAsc(GeoPlatformAccount::getSortOrder)
                .orderByDesc(GeoPlatformAccount::getId);
        Page<GeoPlatformAccount> page = accountMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        List<GeoPlatformAccountListVO> rows = page.getRecords().stream().map(this::toListVo).toList();
        return new PageResult<>(page.getTotal(), rows);
    }

    @Override
    public GeoPlatformAccountListVO getById(Long id) {
        return toListVo(require(id));
    }

    @Override
    public void create(GeoPlatformAccountDTO dto) {
        GeoPlatform platform = platformService.getById(dto.getPlatformId());
        String account = dto.getAccount().trim();
        assertAccountUnique(platform.getId(), account, null);

        GeoPlatformAccount row = new GeoPlatformAccount();
        fillCommon(row, dto, platform);
        row.setPassword(nz(dto.getPassword()).trim());
        accountMapper.insert(row);
    }

    @Override
    public void update(GeoPlatformAccountDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("账号ID不能为空");
        }
        GeoPlatformAccount row = require(dto.getId());
        GeoPlatform platform = platformService.getById(dto.getPlatformId());
        String account = dto.getAccount().trim();
        assertAccountUnique(platform.getId(), account, dto.getId());

        fillCommon(row, dto, platform);
        if (Boolean.TRUE.equals(dto.getClearPassword())) {
            row.setPassword("");
        } else if (StringUtils.hasText(dto.getPassword()) && !dto.getPassword().contains("****")) {
            row.setPassword(dto.getPassword().trim());
        }
        accountMapper.updateById(row);
    }

    @Override
    public void delete(Long id) {
        require(id);
        accountMapper.deleteById(id);
    }

    private void fillCommon(GeoPlatformAccount row, GeoPlatformAccountDTO dto, GeoPlatform platform) {
        row.setPlatformId(platform.getId());
        row.setPlatformName(platform.getPlatformName());
        row.setAccount(dto.getAccount().trim());
        row.setAccountNickname(nz(dto.getAccountNickname()).trim());
        applyUser(dto.getManagerUserId(), row::setManagerUserId, row::setManagerName);
        applyUser(dto.getHolderUserId(), row::setHolderUserId, row::setHolderName);
        applyUser(dto.getOpenerUserId(), row::setOpenerUserId, row::setOpenerName);
        row.setRecharged(dto.getRecharged() != null && dto.getRecharged() == 1 ? 1 : 0);
        row.setOpenTime(dto.getOpenTime());
        row.setExpireTime(dto.getExpireTime());
        row.setLoginMethod(nz(dto.getLoginMethod()).trim());
        row.setVerified(dto.getVerified() != null && dto.getVerified() == 1 ? 1 : 0);
        row.setVerifyMethod(nz(dto.getVerifyMethod()).trim());
        row.setBindPhone(nz(dto.getBindPhone()).trim());
        row.setBindEmail(nz(dto.getBindEmail()).trim());
        row.setAccountStatus(normalizeStatus(dto.getAccountStatus()));
        row.setLastLoginTime(dto.getLastLoginTime());
        row.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        row.setRemark(dto.getRemark());
    }

    private void applyUser(Long userId, java.util.function.Consumer<Long> idSetter,
                           java.util.function.Consumer<String> nameSetter) {
        if (userId == null) {
            idSetter.accept(null);
            nameSetter.accept("");
            return;
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("关联用户不存在: " + userId);
        }
        idSetter.accept(user.getUserId());
        nameSetter.accept(userDisplayName(user));
    }

    private void assertAccountUnique(Long platformId, String account, Long excludeId) {
        LambdaQueryWrapper<GeoPlatformAccount> wrapper = new LambdaQueryWrapper<GeoPlatformAccount>()
                .eq(GeoPlatformAccount::getPlatformId, platformId)
                .eq(GeoPlatformAccount::getAccount, account);
        if (excludeId != null) {
            wrapper.ne(GeoPlatformAccount::getId, excludeId);
        }
        if (accountMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("该平台下账号已存在");
        }
    }

    private GeoPlatformAccount require(Long id) {
        GeoPlatformAccount row = accountMapper.selectById(id);
        if (row == null) {
            throw new BusinessException("平台账号不存在");
        }
        return row;
    }

    private GeoPlatformAccountListVO toListVo(GeoPlatformAccount row) {
        GeoPlatformAccountListVO vo = new GeoPlatformAccountListVO();
        vo.setId(row.getId());
        vo.setPlatformId(row.getPlatformId());
        vo.setPlatformName(row.getPlatformName());
        vo.setAccount(row.getAccount());
        boolean hasPwd = StringUtils.hasText(row.getPassword());
        vo.setHasPassword(hasPwd);
        vo.setPasswordMasked(hasPwd ? maskPassword(row.getPassword()) : "");
        vo.setAccountNickname(row.getAccountNickname());
        vo.setManagerUserId(row.getManagerUserId());
        vo.setManagerName(row.getManagerName());
        vo.setHolderUserId(row.getHolderUserId());
        vo.setHolderName(row.getHolderName());
        vo.setOpenerUserId(row.getOpenerUserId());
        vo.setOpenerName(row.getOpenerName());
        vo.setRecharged(row.getRecharged());
        vo.setOpenTime(row.getOpenTime());
        vo.setExpireTime(row.getExpireTime());
        vo.setLoginMethod(row.getLoginMethod());
        vo.setVerified(row.getVerified());
        vo.setVerifyMethod(row.getVerifyMethod());
        vo.setBindPhone(row.getBindPhone());
        vo.setBindEmail(row.getBindEmail());
        vo.setAccountStatus(row.getAccountStatus());
        vo.setLastLoginTime(row.getLastLoginTime());
        vo.setSortOrder(row.getSortOrder());
        vo.setRemark(row.getRemark());
        return vo;
    }

    private static String normalizeStatus(String raw) {
        if (STATUS_DISABLED.equals(raw) || STATUS_EXPIRED.equals(raw)) {
            return raw;
        }
        return STATUS_NORMAL;
    }

    private static String maskPassword(String password) {
        String p = password.trim();
        if (p.length() <= 4) {
            return "****";
        }
        return "****" + p.substring(p.length() - 4);
    }

    private static String userDisplayName(SysUser user) {
        if (user == null) {
            return "";
        }
        if (StringUtils.hasText(user.getNickname())) {
            return user.getNickname().trim();
        }
        return user.getUsername() == null ? "" : user.getUsername().trim();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
