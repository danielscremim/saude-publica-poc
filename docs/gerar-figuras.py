#!/usr/bin/env python3
"""
Gera as figuras do TCC a partir dos CSVs medidos em 19-20/09/2026.

    python docs/gerar-figuras.py

Saida: docs/figuras/*.svg e *.pdf (vetorial, para impressao).

Paleta validada pelo validador de acessibilidade (modo claro, superficie #fcfcfb):
separacao CVD e visao normal acima do piso em todos os pares adjacentes. As duas
cores com contraste abaixo de 3:1 (aqua e amarelo) sempre aparecem com rotulo
direto ou legenda, que e o alivio exigido nesse caso.
"""
import csv
import os
from datetime import datetime, timezone
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.ticker import FuncFormatter

BASE = os.path.dirname(os.path.abspath(__file__))
DADOS = os.path.join(BASE, "dados-figuras")
SAIDA = os.path.join(BASE, "figuras")
os.makedirs(SAIDA, exist_ok=True)

# ---------------------------------------------------------------- estilo
# Conforme a Tabela 8 do Manual de Normas USP/Esalq: graficos SEM linhas de
# grade, SEM borda, SEM preenchimento e SEM titulo (o titulo vai na legenda,
# abaixo da figura, no documento). Eixos principais em linha solida preta de
# 1,5 pt e titulos de eixo em Arial tamanho 11 ou menor, cor preta.
SUP = "#ffffff"        # sem preenchimento
TINTA = "#000000"      # cor preta, exigida pela norma
TINTA2 = "#000000"
GRADE = "#bfbfbf"      # usado apenas em marcacoes auxiliares, nunca como grade
S = ["#2a78d6", "#eb6834", "#1baf7a", "#eda100"]   # categoricas, ordem fixa

plt.rcParams.update({
    "figure.facecolor": SUP, "axes.facecolor": SUP, "savefig.facecolor": SUP,
    "font.family": "Arial",
    "font.sans-serif": ["Arial", "Liberation Sans", "DejaVu Sans"],
    "font.size": 10, "axes.labelsize": 11, "axes.titlesize": 11,
    "axes.labelcolor": TINTA, "text.color": TINTA,
    "xtick.color": TINTA, "ytick.color": TINTA,
    "xtick.labelsize": 9, "ytick.labelsize": 9,
    "axes.edgecolor": TINTA, "axes.linewidth": 1.5,
    "axes.grid": False,
    "legend.frameon": False, "legend.fontsize": 9,
    "figure.dpi": 200, "savefig.dpi": 200,
})


def eixos(ax):
    """Apenas os eixos principais, em linha solida preta de 1,5 pt (norma)."""
    for lado in ("top", "right"):
        ax.spines[lado].set_visible(False)
    for lado in ("bottom", "left"):
        ax.spines[lado].set_visible(True)
        ax.spines[lado].set_color(TINTA)
        ax.spines[lado].set_linewidth(1.5)
    ax.grid(False)
    ax.tick_params(colors=TINTA, width=1.2)


def salvar(fig, nome):
    for ext in ("svg", "pdf", "png"):
        fig.savefig(os.path.join(SAIDA, f"{nome}.{ext}"), bbox_inches="tight")
    plt.close(fig)
    print(f"  {nome}.svg / .pdf")


def ler(nome):
    with open(os.path.join(DADOS, nome), encoding="utf-8", errors="replace") as f:
        return list(csv.reader(f))


def ts(s):
    return datetime.fromisoformat(s.replace("Z", "+00:00")).astimezone(timezone.utc)


# ================================================================ FIGURA 1
# Curva de saturacao: P95 por taxa de chegada, duas execucoes independentes.
# Forma: linha - a variavel do eixo x e continua e a mensagem e a INFLEXAO.
def fig1():
    def curva(arq):
        x, y = [], []
        for r in ler(arq)[1:]:
            if len(r) >= 3 and float(r[2]) > 0:
                x.append(int(r[1])); y.append(float(r[2]))
        return x, y

    x1, y1 = curva("ruptura-exec1.csv")
    x2, y2 = curva("ruptura-exec2.csv")

    fig, ax = plt.subplots(figsize=(6.8, 3.8))
    ax.plot(x1, y1, "-o", color=S[0], lw=2, ms=5, label="Execução 1")
    ax.plot(x2, y2, "-s", color=S[1], lw=2, ms=5, label="Execução 2")

    ax.axhline(2000, color=TINTA2, ls="--", lw=1)
    ax.text(230, 2120, "limite adotado para a PoC: 2.000 ms",
            color=TINTA2, fontsize=8)

    ax.axvline(1400, color=GRADE, lw=8, zorder=0)
    ax.annotate("capacidade\nsustentada\n1.400 req/s",
                xy=(1400, 462), xytext=(900, 2600), color=TINTA, fontsize=8,
                ha="center",
                arrowprops=dict(arrowstyle="->", color=TINTA2, lw=0.9))

    eixos(ax)
    ax.set_xlabel("Taxa de chegada (requisições por segundo)")
    ax.set_ylabel("Latência P95 (ms)")
    ax.set_xticks(x2)
    mil = FuncFormatter(lambda v, _: f"{v:,.0f}".replace(",", "."))
    ax.yaxis.set_major_formatter(mil)
    ax.xaxis.set_major_formatter(mil)
    ax.legend(loc="upper left")
    salvar(fig, "fig1-curva-saturacao")


# ================================================================ FIGURA 2
# Latencia e fila de conexoes no mesmo eixo x, em paineis separados.
# NUNCA eixo duplo: duas medidas de escalas diferentes -> dois paineis.
def fig2():
    INICIO = ts("2026-09-20T07:22:06Z")
    AQUEC, PASSO = 120, 130
    DEGRAUS = list(range(200, 2001, 200))

    def degrau(t):
        s = (t - INICIO).total_seconds() - AQUEC
        return int(s // PASSO) if 0 <= s // PASSO < len(DEGRAUS) else None

    fila = {d: 0 for d in range(len(DEGRAUS))}
    for r in ler("pools-ruptura.csv")[1:]:
        if len(r) < 4 or r[1] != "hikari_pendentes":
            continue
        try:
            d, v = degrau(ts(r[0])), float(r[3].strip('"'))
        except Exception:
            continue
        if d is not None and v > fila[d]:
            fila[d] = v

    p95 = [float(r[2]) for r in ler("ruptura-exec2.csv")[1:] if float(r[2]) > 0]

    fig, (a1, a2) = plt.subplots(2, 1, figsize=(6.8, 4.6), sharex=True,
                                 gridspec_kw={"height_ratios": [1.3, 1]})
    a1.plot(DEGRAUS[:len(p95)], p95, "-o", color=S[0], lw=2, ms=5)
    a1.axhline(2000, color=TINTA2, ls="--", lw=1)
    a1.set_ylabel("Latência P95 (ms)")
    eixos(a1)

    cores = [S[1] if fila[d] > 0 else GRADE for d in range(len(DEGRAUS))]
    a2.bar(DEGRAUS, [fila[d] for d in range(len(DEGRAUS))], width=120,
           color=cores)
    for d, rps in enumerate(DEGRAUS):
        if fila[d] > 0:
            a2.text(rps, fila[d] + 1.5, f"{int(fila[d])}", ha="center",
                    fontsize=8, color=TINTA)
    a2.set_ylabel("Requisições na fila\n(máximo observado)")
    a2.set_xlabel("Taxa de chegada (requisições por segundo)")
    a2.set_xticks(DEGRAUS)
    mil = FuncFormatter(lambda v, _: f"{v:,.0f}".replace(",", "."))
    a1.yaxis.set_major_formatter(mil)
    a2.xaxis.set_major_formatter(mil)
    eixos(a2)
    a2.text(200, max(fila.values()) * 0.82,
            "fila zero até 1.200 req/s", color=TINTA2, fontsize=8)
    salvar(fig, "fig2-fila-conexoes")


# ================================================================ FIGURA 3
# Replicas por servico ao longo do tempo. Degraus, porque replica e discreta.
def fig3():
    servicos = ["history-service", "consent-service", "patient-service", "result-service"]
    serie = {s: ([], []) for s in servicos}
    t0 = None
    for r in ler("hpa-bateria.csv")[1:]:
        if len(r) < 3 or r[1] not in serie:
            continue
        try:
            t, v = ts(r[0]), int(r[2])
        except Exception:
            continue
        if t0 is None:
            t0 = t
        m = (t - t0).total_seconds() / 60
        if m > 85:
            continue
        serie[r[1]][0].append(m)
        serie[r[1]][1].append(v)

    fig, ax = plt.subplots(figsize=(6.8, 3.9))
    # Rotulo direto e impossivel aqui: as quatro series convergem para 10
    # replicas e os textos se sobrepoem. A identidade fica na legenda, que
    # tambem cumpre o alivio exigido pelas cores de menor contraste.
    for i, s in enumerate(servicos):
        x, y = serie[s]
        if not x:
            continue
        ax.step(x, y, where="post", color=S[i], lw=1.8,
                label=s.replace("-service", ""))

    ax.axvspan(0, 10, color=GRADE, alpha=0.45, zorder=0)
    for lim in (10, 46):
        ax.axvline(lim, color=GRADE, ls=":", lw=1, zorder=0)
    ax.text(5, 10.7, "aquec.", ha="center", fontsize=8, color=TINTA2)
    ax.text(28, 10.7, "cenário A — 150 VUs", ha="center", fontsize=8, color=TINTA2)
    ax.text(66, 10.7, "cenário B — 1000 VUs", ha="center", fontsize=8, color=TINTA2)

    eixos(ax)
    ax.set_xlabel("Tempo desde o início da bateria (min)")
    ax.set_ylabel("Réplicas ativas")
    ax.set_ylim(0, 11.8)
    ax.set_xlim(0, 88)
    ax.legend(loc="upper center", bbox_to_anchor=(0.5, -0.20), ncol=4,
              handlelength=1.6, columnspacing=1.6)
    salvar(fig, "fig3-replicas-hpa")


# ================================================================ FIGURA 4
# Drenagem da fila: registros de auditoria acumulados, com a carga encerrada.
def fig4():
    FIM = ts("2026-09-19T17:39:05Z")
    x, y, t0 = [], [], None
    for r in ler("tabelas.csv")[1:]:
        if len(r) < 4 or r[2] != "audit_log":
            continue
        try:
            t, v = ts(r[0]), int(r[3])
        except Exception:
            continue
        if t0 is None:
            t0 = t
        m = (t - t0).total_seconds() / 60
        if m > 190:
            continue
        x.append(m); y.append(v / 1e6)

    fim_min = (FIM - t0).total_seconds() / 60

    fig, ax = plt.subplots(figsize=(6.8, 3.6))
    ax.plot(x, y, color=S[0], lw=2)
    ax.axvline(fim_min, color=S[1], lw=1.6, ls="--")
    ax.text(fim_min + 1.5, 0.55, "carga encerrada", color=S[1], fontsize=8,
            rotation=0, va="bottom")

    dentro = [(a, b) for a, b in zip(x, y) if a >= fim_min]
    if dentro:
        ax.fill_between([a for a, _ in dentro], 0, [b for _, b in dentro],
                        color=S[1], alpha=0.12)
        ax.annotate("≈ 1,40 milhão de eventos\ndrenados com a carga parada\n(26 min, ~940 eventos/s)",
                    xy=(fim_min + 12, 2.75), xytext=(fim_min - 82, 3.12),
                    fontsize=8, color=TINTA,
                    arrowprops=dict(arrowstyle="->", color=TINTA2, lw=0.9))

    eixos(ax)
    ax.set_xlabel("Tempo desde o início da coleta (min)")
    ax.set_ylabel("Registros de auditoria\npersistidos (milhões)")
    ax.set_ylim(0, 3.7)
    salvar(fig, "fig4-drenagem-kafka")


# ================================================================ FIGURA 5
# Bytes por resposta. Barras: comparacao de magnitude entre duas categorias.
def fig5():
    rot = ["REST\n(resposta completa)", "GraphQL\n(campos declarados)"]
    val = [5522.3, 2651.7]

    fig, ax = plt.subplots(figsize=(4.6, 3.4))
    b = ax.bar(rot, val, width=0.55, color=[S[0], S[2]])
    for r, v in zip(b, val):
        ax.text(r.get_x() + r.get_width() / 2, v + 90,
                f"{v:,.0f} B".replace(",", "."), ha="center", fontsize=9,
                color=TINTA)
    # Seta no vao entre as barras: sobre a barra ela cruzaria o rotulo de valor.
    ax.annotate("", xy=(0.5, 2651.7), xytext=(0.5, 5522.3),
                arrowprops=dict(arrowstyle="<->", color=TINTA2, lw=1))
    ax.text(0.56, 4090, "−51,98%", color=TINTA, fontsize=10, va="center")

    eixos(ax)
    ax.set_ylabel("Bytes por resposta (média de 3 rodadas)")
    ax.set_ylim(0, 6400)
    salvar(fig, "fig5-bytes-rest-graphql")


# ================================================================ FIGURA 6
# Eficiencia por nucleo. A mensagem e que MAIS pods produziram MENOS trabalho.
def fig6():
    rot = ["43 pods\n(19/09)", "45 pods\n(maxReplicas=6)", "57 pods\n(maxReplicas=10)"]
    # vazao (req/s) / CPU do no (nucleos) no pico de cada execucao
    val = [1324.9 / 28.07, 1225.2 / 30.07, 130.0 / 31.25]
    cor = [S[0], S[0], S[1]]

    fig, ax = plt.subplots(figsize=(5.4, 3.4))
    b = ax.bar(rot, val, width=0.55, color=cor)
    for r, v in zip(b, val):
        ax.text(r.get_x() + r.get_width() / 2, v + 1.4, f"{v:.1f}".replace(".", ","),
                ha="center", fontsize=9, color=TINTA)

    eixos(ax)
    ax.set_ylabel("Requisições por segundo\npor núcleo de CPU")
    ax.set_ylim(0, 60)
    ax.plot([0, 1], [55, 55], color=TINTA2, lw=0.8)
    ax.text(0.5, 56.2, "configurações saudáveis", ha="center", fontsize=8,
            color=TINTA2)
    ax.text(2, 12, "colapso", ha="center", fontsize=8, color=S[1])
    salvar(fig, "fig6-eficiencia-por-nucleo")


if __name__ == "__main__":
    print("Gerando figuras em docs/figuras/")
    for f in (fig1, fig2, fig3, fig4, fig5, fig6):
        try:
            f()
        except Exception as e:
            print(f"  ERRO em {f.__name__}: {type(e).__name__}: {e}")
    print("Concluido.")
