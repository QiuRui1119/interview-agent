package com.interview.infrastructure.Mapper;


import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import com.interview.modules.resume.model.ResumeAnalysisEntity;
import com.interview.modules.interview.model.ResumeAnalysisResponse;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/**
 * 简历相关的对象映射器
 * 使用MapStruct自动生成转换代码
 * <p>
 * 注意：JSON字段(strengthsJson, suggestionsJson)需要在Service层手动处理
 */
@Mapper(componentModel = "spring",unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ResumeMapper {

    // ========== 工具方法 ==========

    @Named("nullToZero")
    default int nullToZero(Integer value) {
        return value != null ? value : 0;
    }

    // ========== ScoreDetail 映射 ==========

    /**
     * 将实体基础字段映射到DTO的ScoreDetail
     */
    @Mapping(target = "contentScore", source = "contentScore", qualifiedByName = "nullToZero")
    @Mapping(target = "structureScore", source = "structureScore", qualifiedByName = "nullToZero")
    @Mapping(target = "skillMatchScore", source = "skillMatchScore", qualifiedByName = "nullToZero")
    @Mapping(target = "expressionScore", source = "expressionScore", qualifiedByName = "nullToZero")
    @Mapping(target = "projectScore", source = "projectScore", qualifiedByName = "nullToZero")
    ResumeAnalysisResponse.ScoreDetail mapToScoreDetail(ResumeAnalysisEntity entity);


}
