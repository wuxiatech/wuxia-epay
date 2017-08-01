/**
 * Alipay.com Inc.
 * Copyright (c) 2004-2014 All Rights Reserved.
 */
package cn.wuxia.epay.alipay.dispatcher;

import java.io.IOException;
import java.util.Map;

import org.jdom2.JDOMException;

import com.alipay.api.internal.util.StringUtils;

import cn.wuxia.common.util.XMLUtil;
import cn.wuxia.epay.alipay.common.MyException;
import cn.wuxia.epay.alipay.constants.AlipayServiceEventConstants;
import cn.wuxia.epay.alipay.constants.AlipayServiceNameConstants;
import cn.wuxia.epay.alipay.executor.ActionExecutor;
import cn.wuxia.epay.alipay.executor.InAlipayAsyncMsgSendExecutor;
import cn.wuxia.epay.alipay.executor.InAlipayChatTextExecutor;
import cn.wuxia.epay.alipay.executor.InAlipayDIYQRCodeEnterExecutor;
import cn.wuxia.epay.alipay.executor.InAlipayDefaultExecutor;
import cn.wuxia.epay.alipay.executor.InAlipayEnterExecutor;
import cn.wuxia.epay.alipay.executor.InAlipayFollowExecutor;
import cn.wuxia.epay.alipay.executor.InAlipayUnFollowExecutor;
import cn.wuxia.epay.alipay.executor.InAlipayVerifyExecutor;


/**
 * 业务动作分发器
 * 
 * @author taixu.zqq
 * @version $Id: Dispatcher.java, v 0.1 2014年7月24日 下午4:59:58 taixu.zqq Exp $
 */
public class Dispatcher {

    /**
     * 根据业务参数获取业务执行器
     * 
     * @param params
     * @return
     * @throws MyException
     */
    public static ActionExecutor getExecutor(Map<String, String> params) throws MyException {
        //获取服务信息
        String service = params.get("service");
        if (StringUtils.isEmpty(service)) {
            throw new MyException("无法取得服务名");
        }
        //获取内容信息
        String bizContent = params.get("biz_content");
        if (StringUtils.isEmpty(bizContent)) {
            throw new MyException("无法取得业务内容信息");
        }

        //将XML转化成json对象
        Map bizContentJson = null;
        try {
            bizContentJson = XMLUtil.doXMLParse(bizContent);
        } catch (JDOMException | IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        // 1.获取消息类型信息 
        String msgType = (String)bizContentJson.get("MsgType");
        if (StringUtils.isEmpty(msgType)) {
            throw new MyException("无法取得消息类型");
        }

        // 2.根据消息类型(msgType)进行执行器的分类转发
        //  2.1 纯文本聊天类型
        if ("text".equals(msgType)) {

            return new InAlipayChatTextExecutor(bizContentJson);

            // 2.2 事件类型
        } else if ("event".equals(msgType)) {

            return getEventExecutor(service, bizContentJson);

        } else {

            // 2.3 后续支付宝还会新增其他类型，因此默认返回ack应答
            return new InAlipayDefaultExecutor(bizContentJson);
        }

    }

    /**
     * 根据事件类型细化查找对应执行器
     * 
     * @param service
     * @param bizContentJson
     * @return
     * @throws MyException
     */
    private static ActionExecutor getEventExecutor(String service, Map bizContentJson)
                                                                                             throws MyException {
        // 1. 获取事件类型
        String eventType = (String)bizContentJson.get("EventType");

        if (StringUtils.isEmpty(eventType)) {
            throw new MyException("无法取得事件类型");
        }

        // 2.根据事件类型再次区分服务类型
        // 2.1 激活验证开发者模式
        if (AlipayServiceNameConstants.ALIPAY_CHECK_SERVICE.equals(service)
            && eventType.equals(AlipayServiceEventConstants.VERIFYGW_EVENT)) {

            return new InAlipayVerifyExecutor();

            // 2.2 其他消息通知类 
        } else if (AlipayServiceNameConstants.ALIPAY_PUBLIC_MESSAGE_NOTIFY.equals(service)) {

            return getMsgNotifyExecutor(eventType, bizContentJson);

            // 2.3 对于后续支付宝可能新增的类型，统一默认返回AKC响应
        } else {
            return new InAlipayDefaultExecutor(bizContentJson);
        }
    }

    /**
     * 根据事件类型(eventType)进行执行器的分类转发
     * 
     * @param eventType
     * @param bizContentJson
     * @return
     * @throws MyException
     */
    private static ActionExecutor getMsgNotifyExecutor(String eventType, Map bizContentJson)
                                                                                                   throws MyException {
        if (eventType.equals(AlipayServiceEventConstants.FOLLOW_EVENT)) {

            // 服务窗关注事件
            return new InAlipayFollowExecutor(bizContentJson);

        } else if (eventType.equals(AlipayServiceEventConstants.UNFOLLOW_EVENT)) {

            //  服务窗取消关注事件
            return new InAlipayUnFollowExecutor(bizContentJson);

            //根据actionParam进行执行器的转发
        } else if (eventType.equals(AlipayServiceEventConstants.CLICK_EVENT)) {

            // 点击事件
            return getClickEventExecutor(bizContentJson);

        } else if (eventType.equals(AlipayServiceEventConstants.ENTER_EVENT)) {

            // 进入事件
            return getEnterEventTypeExecutor(bizContentJson);

        } else {

            // 对于后续支付宝可能新增的类型，统一默认返回AKC响应
            return new InAlipayDefaultExecutor(bizContentJson);
        }

    }

    /**
     * 进入事件执行器
     * 
     * @param bizContentJson
     * @return
     */
    private static ActionExecutor getEnterEventTypeExecutor(Map bizContentJson) {
        try {

            Map param = (Map) bizContentJson.get("ActionParam");
            Map scene = (Map) param.get("scene");

            if (!StringUtils.isEmpty((String)scene.get("sceneId"))) {

                //自定义场景参数进入服务窗事件
                return new InAlipayDIYQRCodeEnterExecutor(bizContentJson);
            } else {

                //普通进入服务窗事件
                return new InAlipayEnterExecutor(bizContentJson);
            }
        } catch (Exception exception) {
            //无法解析sceneId的情况作为普通进入服务窗事件
            return new InAlipayEnterExecutor(bizContentJson);
        }
    }

    /**
     * 点击事件执行器
     * 
     * @param bizContentJson
     * @return
     */
    private static ActionExecutor getClickEventExecutor(Map bizContentJson) {

        String actionParam = (String)bizContentJson.get("ActionParam");

        if ("authentication".equals(actionParam)) {

            //申请开发者会员绑定事件:  actionParam支付宝服务窗固定值
            //TODO 开发者自行实现，开发者若不需要绑定开发者会员，可无需实现
            return null;
        } else if ("delete".equals(actionParam)) {

            //删除开发者会员绑定事件：actionParam支付宝服务窗固定值
            //TODO 开发者自行实现，开发者若不需要绑定开发者会员，可无需实现
            return null;

            // 除上述支付宝保留key外，其他key为开发者配置菜单时自定义
        } else {

            //服务窗菜单点击事件：actionParam开发者可以自定义，除不能与authentication和delete重复及特殊字符外,没有其他要求
            //"async_image_text"即为开发者自定义的异步发送图文消息的菜单key，这里只是个样例而已
            if ("async_image_text".equals(actionParam)) {

                // 根据配置的菜单具体含义，开发者进行业务应答，这里只是个样例
                return new InAlipayAsyncMsgSendExecutor(bizContentJson);

                // 其他菜单key请开发者自行补充,并执行开发相应响应
            } else if ("xxx".equals(actionParam)) {

                // TODO 开发者根据key自行开发相应响应，这里只是个样例
                return null;
            } else {
                // TODO 开发者根据key自行开发相应响应，这里只是个样例
                return null;
            }
        }
    }
}