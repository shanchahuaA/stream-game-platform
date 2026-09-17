
package com.stream.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class Games {
    private Integer id;
    private String platform;
    private String platformGameId;
    private String gname;
    private String coverUrl;
    private String shopUrl;
    private Double originalPrice;
    private Double finalPrice;
    private Integer discountPercent;
    private Integer topSellerRank;
    private Integer discountRank;
    private LocalDateTime lastSyncTime;
    private Integer reviewCount;
    private BigDecimal positiveRate;
    private String reviewTier;

    public Games(String gname, String shopUrl, String platformGameId) {
        this.gname = gname;
        this.shopUrl = shopUrl;
        this.platformGameId = platformGameId;

    }
}

