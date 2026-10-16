package com.zhijian.risk.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("whistle_reports")
public class WhistleReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orgId;
    private String kind = "daily";
    private String period = "";
    private String title;
    private String summary = "";
    private String markdown = "";
    private String batchStatsJson = "{}";
    private int clueCount;
    private int level1;
    private int level2;
    private int level3;
    private int level4;
    private String status = "done";
    private int progress = 100;
    private String partnerName = "";
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public Long getOrgId(){return orgId;} public void setOrgId(Long orgId){this.orgId=orgId;}
    public String getKind(){return kind;} public void setKind(String kind){this.kind=kind;}
    public String getPeriod(){return period;} public void setPeriod(String period){this.period=period;}
    public String getTitle(){return title;} public void setTitle(String title){this.title=title;}
    public String getSummary(){return summary;} public void setSummary(String summary){this.summary=summary;}
    public String getMarkdown(){return markdown;} public void setMarkdown(String markdown){this.markdown=markdown;}
    public String getBatchStatsJson(){return batchStatsJson;} public void setBatchStatsJson(String batchStatsJson){this.batchStatsJson=batchStatsJson;}
    public int getClueCount(){return clueCount;} public void setClueCount(int clueCount){this.clueCount=clueCount;}
    public int getLevel1(){return level1;} public void setLevel1(int level1){this.level1=level1;}
    public int getLevel2(){return level2;} public void setLevel2(int level2){this.level2=level2;}
    public int getLevel3(){return level3;} public void setLevel3(int level3){this.level3=level3;}
    public int getLevel4(){return level4;} public void setLevel4(int level4){this.level4=level4;}
    public String getStatus(){return status;} public void setStatus(String status){this.status=status;}
    public int getProgress(){return progress;} public void setProgress(int progress){this.progress=progress;}
    public String getPartnerName(){return partnerName;} public void setPartnerName(String partnerName){this.partnerName=partnerName;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime createdAt){this.createdAt=createdAt;}
    public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime updatedAt){this.updatedAt=updatedAt;}
}
