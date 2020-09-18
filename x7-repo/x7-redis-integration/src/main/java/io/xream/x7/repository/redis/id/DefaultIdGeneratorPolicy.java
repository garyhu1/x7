/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.xream.x7.repository.redis.id;

import io.xream.sqli.annotation.X;
import io.xream.sqli.repository.api.BaseRepository;
import io.xream.sqli.repository.api.ResultMapRepository;
import io.xream.sqli.builder.Criteria;
import io.xream.sqli.builder.CriteriaBuilder;
import io.xream.sqli.builder.ReduceType;
import io.xream.sqli.parser.BeanElement;
import io.xream.sqli.parser.Parsed;
import io.xream.sqli.parser.Parser;
import io.xream.x7.base.util.VerifyUtil;
import io.xream.x7.repository.id.IdGeneratorPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class DefaultIdGeneratorPolicy implements IdGeneratorPolicy {

    private Logger logger = LoggerFactory.getLogger(IdGeneratorPolicy.class);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public long createId(String clzName) {
        return this.stringRedisTemplate.opsForHash().increment(ID_MAP_KEY,clzName,1);
    }


    @Override
    public void onStart(List<BaseRepository> repositoryList) {
        if (repositoryList == null)
            return;

        long startTme = System.currentTimeMillis();
        logger.info("x7-repo/x7-id-generator starting.... \n");

        final String idGeneratorScript = "local hk = KEYS[1] " +
                "local key = KEYS[2] " +
                "local id = ARGV[1] " +
                "local existId = redis.call('hget',hk,key) " +
                "if tonumber(existId) == nil then existId = '0' end " +
                "if tonumber(id) > tonumber(existId) " +
                "then " +
                "redis.call('hset',hk,key,tostring(id)) " +
                "return tonumber(id) "+
                "end " +
                "return tonumber(existId)";

        RedisScript<Long> redisScript = new DefaultRedisScript<Long>() {

            @Override
            public String getSha1(){
                return VerifyUtil.toMD5("id_map_key");
            }

            @Override
            public Class<Long> getResultType() {
                return Long.class;
            }

            @Override
            public String getScriptAsString() {
                return  idGeneratorScript;
            }
        };

        for (BaseRepository baseRepository : repositoryList) {
            CriteriaBuilder.ResultMapBuilder builder = CriteriaBuilder.resultMapBuilder();
            Class clzz = baseRepository.getClzz();
            if (clzz == Void.class)
                continue;
            Parsed parsed = Parser.get(clzz);
            String key = parsed.getKey(X.KEY_ONE);
            BeanElement be = parsed.getElement(key);
            if (be.getClz() == String.class)
                continue;
            builder.reduce(ReduceType.MAX, be.getProperty()).paged().ignoreTotalRows();
            Criteria.ResultMapCriteria ResultMapCriteria = builder.build();
            List<Long> idList = null;
            if (baseRepository instanceof ResultMapRepository){
                ResultMapRepository resultMapRepository = (ResultMapRepository) baseRepository;
                idList = resultMapRepository.listPlainValue(Long.class,ResultMapCriteria);
            }

            Long maxId = idList.stream().filter(id -> id != null).findFirst().orElse(0L);
            String name = baseRepository.getClzz().getName();

            logger.info("Db    : " + name + ".maxId = " + maxId);

            List<String> keys = Arrays.asList(IdGeneratorPolicy.ID_MAP_KEY,name);
            long result = this.stringRedisTemplate.execute(redisScript,keys,String.valueOf(maxId));

            logger.info("Redis : " + name + ".maxId = " + result);

        }
        logger.info("..................................................");
        long endTime = System.currentTimeMillis();
        logger.info("x7-repo/x7-id-generator started, cost time: " + (endTime-startTme) +"ms\n\n");
    }

}
