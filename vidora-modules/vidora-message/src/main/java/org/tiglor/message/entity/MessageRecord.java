package org.tiglor.message.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("message_record")
public class MessageRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer msgType;
    private Long senderId;
    private Long receiverId;
    private String content;
    private String extra;
    private Integer isRead;
    private LocalDateTime readTime;
    private Integer status;
    private LocalDateTime createTime;
}
