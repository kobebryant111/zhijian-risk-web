package com.zhijian.risk.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("risk_clues")
public class RiskClue {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orgId;
    private String publicId;
    private Long partnerId;
    private String partnerName;
    private String partnerType = "";
    private int level = 3;
    private String title;
    private String summary = "";
    private String eventDate = "";
    private String owner = "合作风控";
    private int progress = 0;
    private String status = "待复核";
    private String sourceSystem = "";
    private String sourceUrl = "";
    private String credibility = "C";
    private String ruleId = "";
    private String legalBasis = "";
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public Long getOrgId(){return orgId;} public void setOrgId(Long orgId){this.orgId=orgId;}
    public String getPublicId(){return publicId;} public void setPublicId(String publicId){this.publicId=publicId;}
    public Long getPartnerId(){return partnerId;} public void setPartnerId(Long partnerId){this.partnerId=partnerId;}
    public String getPartnerName(){return partnerName;} public void setPartnerName(String partnerName){this.partnerName=partnerName;}
    public String getPartnerType(){return partnerType;} public void setPartnerType(String partnerType){this.partnerType=partnerType;}
    public int getLevel(){return level;} public void setLevel(int level){this.level=level;}
    public String getTitle(){return title;} public void setTitle(String title){this.title=title;}
    public String getSummary(){return summary;} public void setSummary(String summary){this.summary=summary;}
    public String getEventDate(){return eventDate;} public void setEventDate(String eventDate){this.eventDate=eventDate;}
    public String getOwner(){return owner;} public void setOwner(String owner){this.owner=owner;}
    public int getProgress(){return progress;} public void setProgress(int progress){this.progress=progress;}
    public String getStatus(){return status;} public void setStatus(String status){this.status=status;}
    public String getSourceSystem(){return sourceSystem;} public void setSourceSystem(String sourceSystem){this.sourceSystem=sourceSystem;}
    public String getSourceUrl(){return sourceUrl;} public void setSourceUrl(String sourceUrl){this.sourceUrl=sourceUrl;}
    public String getCredibility(){return credibility;} public void setCredibility(String credibility){this.credibility=credibility;}
    public String getRuleId(){return ruleId;} public void setRuleId(String ruleId){this.ruleId=ruleId;}
    public String getLegalBasis(){return legalBasis;} public void setLegalBasis(String legalBasis){this.legalBasis=legalBasis;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime createdAt){this.createdAt=createdAt;}
}
