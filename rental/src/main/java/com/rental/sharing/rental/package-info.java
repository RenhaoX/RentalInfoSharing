/**
 * 租房业务模块，与身份及账号模块并列。
 *
 * <p>housing 承载用户端房屋信息的新增、修改、分页筛选和软删除。
 * 浏览行为、评论及管理端后续分别设计模块，本模块不预建相关接口。
 * 跨模块身份访问仅使用 com.rental.sharing.identity.api，不直接访问用户表或认证实现。
 */
package com.rental.sharing.rental;
