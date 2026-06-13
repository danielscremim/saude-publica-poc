package br.usp.esalq.saude.triage.entity;

/**
 * Protocolo de Manchester (simplificado):
 *   RED    - emergencia (atendimento imediato)
 *   ORANGE - muito urgente (~ 10 min)
 *   YELLOW - urgente (~ 60 min)
 *   GREEN  - pouco urgente (~ 120 min)
 *   BLUE   - nao urgente (~ 240 min)
 */
public enum TriagePriority {
    RED, ORANGE, YELLOW, GREEN, BLUE
}
