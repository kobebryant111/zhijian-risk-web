package com.zhijian.risk.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("agent_conversations")
public class AgentConversation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orgId;
    private Long userId;
    private String title = "新对话";
    private String messagesJson = "[]";
    private String status = "idle";
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public Long getOrgId(){return orgId;} public void setOrgId(Long orgId){this.orgId=orgId;}
    public Long getUserId(){return userId;} public void setUserId(Long userId){this.userId=userId;}
    public String getTitle(){return title;} public void setTitle(String title){this.title=title;}
    public String getMessagesJson(){return messagesJson;} public void setMessagesJson(String messagesJson){this.messagesJson=messagesJson;}
    public String getStatus(){return status;} public void setStatus(String status){this.status=status;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime createdAt){this.createdAt=createdAt;}
    public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime updatedAt){this.updatedAt=updatedAt;}
}
