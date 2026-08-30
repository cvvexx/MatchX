package io.cvvexxx.matchx.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BinanceTradeEvent(
        @JsonProperty("e") String eventType,   // Тип события ("trade")
        @JsonProperty("E") long eventTime,    // Время события
        @JsonProperty("s") String symbol,       // Торговая пара ("BTCUSDT")
        @JsonProperty("t") long tradeId,      // ID сделки
        @JsonProperty("p") String price,        // Цена ("61250.50")
        @JsonProperty("q") String quantity,     // Количество ("0.015")
        @JsonProperty("T") long tradeTime,     // Время выполнения сделки
        @JsonProperty("m") boolean isBuyerMaker // Является ли покупатель мейкером
) {}