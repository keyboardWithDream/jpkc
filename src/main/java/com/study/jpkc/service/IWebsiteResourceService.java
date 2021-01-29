package com.study.jpkc.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.study.jpkc.entity.WebsiteResource;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author isharlan.hu@gmail.com
 * @since 2021-01-01
 */
public interface IWebsiteResourceService extends IService<WebsiteResource> {

    /**
     * 通过名称分页查询布局资源
     * @param layoutName 布局名称
     * @param current 当前页码
     * @param size 每页大小
     * @return 分页布局资源
     */
    IPage<WebsiteResource> findWebResourceByLayoutName(Integer current, Integer size, String layoutName);
}
