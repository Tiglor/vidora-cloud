package org.tiglor.recommend.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("recommend_result")
public class RecommendResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long videoId;
    private String scene;
    private BigDecimal score;
    private String algoType;
    private Integer isExposed;
    private Integer isClicked;
    private LocalDateTime createTime;
}
