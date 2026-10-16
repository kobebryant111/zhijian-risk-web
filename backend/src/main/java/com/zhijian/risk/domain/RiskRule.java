package com.zhijian.risk.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("risk_rules")
public class RiskRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orgId;
    private String ruleCode;
    private String partnerType = "common";
    private String riskPoint;
    private int level = 3;
    private String triggerText = "";
    private String dataSource = "";
    private String legalBasis = "";
    private String keywordsJson = "[]";
    private boolean enabled = true;
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public Long getOrgId(){return orgId;} public void setOrgId(Long orgId){this.orgId=orgId;}
    public String getRuleCode(){return ruleCode;} public void setRuleCode(String ruleCode){this.ruleCode=ruleCode;}
    public String getPartnerType(){return partnerType;} public void setPartnerType(String partnerType){this.partnerType=partnerType;}
    public String getRiskPoint(){return riskPoint;} public void setRiskPoint(String riskPoint){this.riskPoint=riskPoint;}
    public int getLevel(){return level;} public void setLevel(int level){this.level=level;}
    public String getTriggerText(){return triggerText;} public void setTriggerText(String triggerText){this.triggerText=triggerText;}
    public String getDataSource(){return dataSource;} public void setDataSource(String dataSource){this.dataSource=dataSource;}
    public String getLegalBasis(){return legalBasis;} public void setLegalBasis(String legalBasis){this.legalBasis=legalBasis;}
    public String getKeywordsJson(){return keywordsJson;} public void setKeywordsJson(String keywordsJson){this.keywordsJson=keywordsJson;}
    public boolean isEnabled(){return enabled;} public void setEnabled(boolean enabled){this.enabled=enabled;}
    public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime updatedAt){this.updatedAt=updatedAt;}
}
