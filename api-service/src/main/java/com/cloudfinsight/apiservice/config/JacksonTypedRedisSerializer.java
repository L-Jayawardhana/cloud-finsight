package com.cloudfinsight.apiservice.config;

import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

public class JacksonTypedRedisSerializer<T> implements RedisSerializer<T> {

    private final JsonMapper jsonMapper;
    private final JavaType javaType;

    public JacksonTypedRedisSerializer(JsonMapper jsonMapper, JavaType javaType) {
        this.jsonMapper = jsonMapper;
        this.javaType = javaType;
    }

    @Override
    public byte[] serialize(T value) throws SerializationException {
        if (value == null) {
            return new byte[0];
        }
        return jsonMapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public T deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        return jsonMapper.readValue(new String(bytes, StandardCharsets.UTF_8), javaType);
    }
}
