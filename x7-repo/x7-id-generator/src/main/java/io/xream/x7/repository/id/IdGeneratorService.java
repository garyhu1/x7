package io.xream.x7.repository.id;


import io.xream.sqli.spi.IdGenerator;

/**
 * @Author Sim
 */
public interface IdGeneratorService extends IdGenerator {
    void setIdGeneratorPolicy(IdGeneratorPolicy policy);
    IdGeneratorPolicy getIdGeneratorPolicy();
}