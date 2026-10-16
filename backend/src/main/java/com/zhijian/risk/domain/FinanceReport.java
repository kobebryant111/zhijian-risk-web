package com.zhijian.risk.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("finance_reports")
public class FinanceReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orgId;
    private String partnerName;
    private String partnerType = "loan";
    private String period = "";
    private String grade = "B";
    private String metricsJson = "{}";
    private String dimensionsJson = "[]";
    private String markdown = "";
    private String summary = "";
    private String status = "done";
    private int progress = 100;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public Long getOrgId(){return orgId;} public void setOrgId(Long orgId){this.orgId=orgId;}
    public String getPartnerName(){return partnerName;} public void setPartnerName(String partnerName){this.partnerName=partnerName;}
    public String getPartnerType(){return partnerType;} public void setPartnerType(String partnerType){this.partnerType=partnerType;}
    public String getPeriod(){return period;} public void setPeriod(String period){this.period=period;}
    public String getGrade(){return grade;} public void setGrade(String grade){this.grade=grade;}
    public String getMetricsJson(){return metricsJson;} public void setMetricsJson(String metricsJson){this.metricsJson=metricsJson;}
    public String getDimensionsJson(){return dimensionsJson;} public void setDimensionsJson(String dimensionsJson){this.dimensionsJson=dimensionsJson;}
    public String getMarkdown(){return markdown;} public void setMarkdown(String markdown){this.markdown=markdown;}
    public String getSummary(){return summary;} public void setSummary(String summary){this.summary=summary;}
    public String getStatus(){return status;} public void setStatus(String status){this.status=status;}
    public int getProgress(){return progress;} public void setProgress(int progress){this.progress=progress;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime createdAt){this.createdAt=createdAt;}
    public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime updatedAt){this.updatedAt=updatedAt;}
}
