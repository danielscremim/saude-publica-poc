package br.usp.esalq.saude.triage.service;

import br.usp.esalq.saude.triage.entity.TriagePriority;
import org.springframework.stereotype.Component;

/**
 * Classificacao automatica simplificada (referencia: Manchester Triage System).
 * Em producao seria substituida por arvore de fluxogramas validada.
 * Aqui usamos regras claras para a PoC; se nenhuma se aplica, cai em GREEN.
 */
@Component
public class TriagePriorityClassifier {

    public TriagePriority classify(Integer bps, Integer bpd, Integer hr, Integer rr,
                                   Double temperature, Integer spo2, Integer pain) {
        // RED: parada cardiorrespiratoria iminente / hipoxemia grave / hipotensao severa
        if (isCritical(spo2, 85) || isCritical(bps, 70) || isAbove(hr, 180) || isAbove(rr, 35)) {
            return TriagePriority.RED;
        }
        // ORANGE: hipertensao grave, taquicardia, hipoxia, febre alta, dor severa
        if (isAbove(bps, 220) || isAbove(hr, 140) || isCritical(spo2, 90)
                || isAbove(temperature, 39.5) || isAbove(pain, 8)) {
            return TriagePriority.ORANGE;
        }
        // YELLOW: alteracoes moderadas
        if (isAbove(bps, 180) || isAbove(hr, 120) || isCritical(spo2, 94)
                || isAbove(temperature, 38.5) || isAbove(pain, 6)) {
            return TriagePriority.YELLOW;
        }
        // BLUE: paciente sem queixa relevante
        if (isLowOrZero(pain) && isBelow(temperature, 37.5)) {
            return TriagePriority.BLUE;
        }
        return TriagePriority.GREEN;
    }

    private static boolean isCritical(Integer v, int threshold) { return v != null && v < threshold; }
    private static boolean isCritical(Double v, double threshold) { return v != null && v < threshold; }
    private static boolean isAbove(Integer v, int threshold) { return v != null && v > threshold; }
    private static boolean isAbove(Double v, double threshold) { return v != null && v > threshold; }
    private static boolean isBelow(Double v, double threshold) { return v == null || v < threshold; }
    private static boolean isLowOrZero(Integer v) { return v == null || v <= 1; }
}
