package br.usp.esalq.saude.history.dto;

import java.util.List;
import java.util.UUID;

/**
 * Raiz da consulta GraphQL. Carrega apenas o que ja foi agregado (paciente e exames);
 * triagens, notificacoes e trilha de auditoria sao resolvidas sob demanda pelos
 * resolvers de campo — se o consumidor nao pedir, nao ha chamada ao servico
 * correspondente (minimizacao de dados).
 *
 * O REST continua devolvendo TimelineResponse (contrato inalterado).
 */
public record PatientView(
        UUID patientUuid,
        PatientDto patient,
        List<TimelineEntry> exams,
        int totalExams
) { }
