package com.iov.platform.modules.geofence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("geofence")
public class Geofence {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    /** 'CIRCLE' | 'POLYGON' */
    private String type;
    private Double centerLng;
    private Double centerLat;
    private Double radiusM;
    /** POLYGON 顶点 JSON 字符串："[{lng, lat}, ...]"，存到 JSONB 列 */
    private String polygon;
    /** PostGIS 几何（仅数据库使用，应用层可不读） */
    private Object geom;
    private Boolean enabled;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
