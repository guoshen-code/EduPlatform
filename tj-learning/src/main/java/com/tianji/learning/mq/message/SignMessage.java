package com.tianji.learning.mq.message;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.ibatis.annotations.ConstructorArgs;

@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")

public class SignMessage {
    private Long userId;
    private Integer points;
}
