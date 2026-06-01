package com.intenthub.infrastructure.config;

import com.intenthub.domain.config.IntentRule;
import com.intenthub.domain.config.LlmPolicy;
import com.intenthub.domain.config.SceneConfig;
import com.intenthub.domain.recognition.DownstreamAction;
import com.intenthub.domain.recognition.Envelope;

import java.util.List;
import java.util.Map;

final class BuiltinSceneConfigFactory {
    private BuiltinSceneConfigFactory() {
    }

    static SceneConfig orderScene(Envelope envelope) {
        return new SceneConfig(
                envelope.tenantId(),
                "order-scene",
                "v1-p1",
                0.60,
                List.of(
                        new IntentRule("ORDER_QUERY", "CONTAINS", "订单", 0.92, "命中订单查询关键词", Map.of()),
                        new IntentRule("ORDER_CANCEL", "REGEX", "取消订单\\s*([A-Za-z0-9]+)", 0.95, "命中取消订单正则", Map.of("slot_hint", "order_id")),
                        new IntentRule("ORDER_CANCEL", "CONTAINS", "取消订单", 0.93, "命中取消订单关键词但缺少订单号", Map.of()),
                        new IntentRule("INVENTORY_EVENT", "CONTAINS", "库存", 0.90, "命中库存事件关键词", Map.of())
                ),
                Map.of(
                        "ORDER_CANCEL", List.of("order_id"),
                        "INVENTORY_EVENT", List.of("sku_id", "event_type")
                ),
                Map.of(
                        "ORDER_QUERY", new DownstreamAction("ORDER_QUERY_SYNC", "NONE", "", false, 0),
                        "ORDER_CANCEL", new DownstreamAction("ORDER_CANCEL_COMMAND", "MQ", "order.command.cancel", true, 3000),
                        "INVENTORY_EVENT", new DownstreamAction("INVENTORY_EVENT_WEBHOOK", "WEBHOOK", "inventory.events", true, 3000)
                ),
                new LlmPolicy(false, "spring-ai-alibaba", "qwen-plus", 2000, 0, "REJECTED")
        );
    }
}
