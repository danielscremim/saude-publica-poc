#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Preenche o Template TCC_PT (USP/Esalq) com o conteudo do trabalho.

    python docs/gerar-tcc.py

Preserva do template: cabecalho com a logo do programa, rodape com numeracao,
margens de 2,5 cm e o tamanho A4. Substitui apenas o corpo.

Normas aplicadas (Manual de Instrucoes e Normas, secoes 15 e 16):
  - Arial 11 preto; enderecos dos autores em Arial 9
  - folha de rosto e resumo com espacamento simples; secoes do corpo com 1,5
  - recuo especial de 1,25 cm na primeira linha, exceto no resumo e referencias
  - titulos de secao em negrito, a esquerda, sem recuo e SEM numeracao
  - subtitulos em negrito com recuo de 1,25 cm
  - figuras: legenda ABAIXO, precedida de "Figura N.", sem ponto final, seguida
    da fonte; espacamento simples
  - tabelas: titulo ACIMA, bordas apenas no cabecalho e no fim, sem cor, sem
    negrito, numeros a direita
"""
import os
import docx
from docx.shared import Pt, Cm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml.ns import qn
from docx.oxml import OxmlElement

BASE = os.path.dirname(os.path.abspath(__file__))
RAIZ = os.path.dirname(BASE)
TPL = os.path.join(RAIZ, "TEMPLATE", "Template TCC_PT (251, 252) (1).docx")
FIG = os.path.join(BASE, "figuras")
SAIDA = os.path.join(RAIZ, "TCC_Daniel_Souza_Scremim.docx")

ARIAL, CORPO, MIUDA = "Arial", Pt(11), Pt(9)
PRETO = RGBColor(0, 0, 0)

ORIENTADOR = "[Nome Completo do Orientador]"   # <<< PREENCHER
TITULACAO_ORIENTADOR = "[Titulação]"           # <<< PREENCHER
EMAIL_ORIENTADOR = "[e-mail do orientador]"    # <<< PREENCHER
CURSO = "MBA em Engenharia de Software"
ANO = "2026"

d = docx.Document(TPL)

# ---------------------------------------------------------------- utilidades
def limpar_corpo():
    """Remove os paragrafos do template sem tocar no sectPr (cabecalho/rodape)."""
    corpo = d.element.body
    for p in list(corpo.findall(qn("w:p"))):
        corpo.remove(p)
    for t in list(corpo.findall(qn("w:tbl"))):
        corpo.remove(t)


def fmt(run, tam=CORPO, negrito=False, italico=False):
    run.font.name = ARIAL
    run.font.size = tam
    run.font.color.rgb = PRETO
    run.bold = negrito
    run.italic = italico
    # Arial tambem para caracteres de alfabetos complexos
    run._element.rPr.rFonts.set(qn("w:eastAsia"), ARIAL)
    return run


def par(texto="", *, align=WD_ALIGN_PARAGRAPH.JUSTIFY, recuo=1.25, ls=1.5,
        negrito=False, tam=CORPO, antes=0, depois=0):
    p = d.add_paragraph()
    pf = p.paragraph_format
    p.alignment = align
    pf.line_spacing = ls
    pf.space_before = Pt(antes)
    pf.space_after = Pt(depois)
    if recuo:
        pf.first_line_indent = Cm(recuo)
    pf.widow_control = True
    if texto:
        fmt(p.add_run(texto), tam=tam, negrito=negrito)
    return p


def titulo_secao(texto):
    """Negrito, a esquerda, sem recuo, sem numeracao (norma 16.3 a 16.8)."""
    par()
    p = par(texto, align=WD_ALIGN_PARAGRAPH.LEFT, recuo=0, negrito=True)
    p.paragraph_format.keep_with_next = True      # nunca orfao no rodape
    return p


def subtitulo(texto):
    """Negrito, recuo especial de 1,25 cm, sem numeracao (norma 16.4 e 16.5)."""
    par()
    p = par(texto, align=WD_ALIGN_PARAGRAPH.LEFT, recuo=1.25, negrito=True)
    p.paragraph_format.keep_with_next = True      # nunca orfao no rodape
    par().paragraph_format.keep_with_next = True
    return p


def figura(arquivo, numero, legenda, fonte="Fonte: Resultados originais da pesquisa",
           nota=None, largura=15.0):
    p = d.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.line_spacing = 1.0
    p.paragraph_format.space_after = Pt(4)
    # Mantem imagem, legenda e fonte no mesmo bloco: a legenda nunca cai
    # sozinha na pagina seguinte
    p.paragraph_format.keep_with_next = True
    p.paragraph_format.keep_together = True
    p.add_run().add_picture(os.path.join(FIG, arquivo), width=Cm(largura))
    # Legenda ABAIXO da figura, sem ponto final apos o texto
    leg = par(align=WD_ALIGN_PARAGRAPH.JUSTIFY, recuo=0, ls=1.0, depois=0)
    leg.paragraph_format.keep_with_next = True
    fmt(leg.add_run(f"Figura {numero}. "))
    fmt(leg.add_run(legenda))
    f = par(align=WD_ALIGN_PARAGRAPH.JUSTIFY, recuo=0, ls=1.0, depois=0)
    f.paragraph_format.keep_with_next = bool(nota)
    fmt(f.add_run(fonte))
    if nota:
        n = par(align=WD_ALIGN_PARAGRAPH.JUSTIFY, recuo=0, ls=1.0, depois=0)
        fmt(n.add_run(nota))
    par(ls=1.0)


def _borda(celula, lados, sz=8):
    tcPr = celula._tc.get_or_add_tcPr()
    borders = OxmlElement("w:tcBorders")
    for lado in ("top", "left", "bottom", "right"):
        el = OxmlElement(f"w:{lado}")
        if lado in lados:
            el.set(qn("w:val"), "single"); el.set(qn("w:sz"), str(sz))
            el.set(qn("w:color"), "000000")
        else:
            el.set(qn("w:val"), "nil")
        borders.append(el)
    tcPr.append(borders)


def tabela(numero, titulo, cabecalho, linhas, larguras,
           fonte="Fonte: Resultados originais da pesquisa", nota=None,
           dir_cols=()):
    """Titulo ACIMA; bordas so no cabecalho e no fim; sem cor e sem negrito."""
    par()
    t = par(align=WD_ALIGN_PARAGRAPH.JUSTIFY, recuo=0, ls=1.0, depois=2)
    t.paragraph_format.keep_with_next = True
    fmt(t.add_run(f"Tabela {numero}. "))
    fmt(t.add_run(titulo))

    tb = d.add_table(rows=1 + len(linhas), cols=len(cabecalho))
    tb.alignment = WD_TABLE_ALIGNMENT.CENTER
    tb.autofit = False
    ultima = len(linhas)
    for r in tb.rows:                       # nenhuma linha parte entre paginas
        trPr = r._tr.get_or_add_trPr()
        trPr.append(OxmlElement("w:cantSplit"))
    for j, txt in enumerate(cabecalho):
        c = tb.cell(0, j)
        c.width = Cm(larguras[j])
        p = c.paragraphs[0]
        p.alignment = WD_ALIGN_PARAGRAPH.LEFT if j == 0 else WD_ALIGN_PARAGRAPH.CENTER
        p.paragraph_format.line_spacing = 1.0
        p.paragraph_format.space_after = Pt(2)
        p.paragraph_format.keep_with_next = True
        fmt(p.add_run(txt))
        _borda(c, ("top", "bottom"))
    for i, linha in enumerate(linhas, start=1):
        for j, txt in enumerate(linha):
            c = tb.cell(i, j)
            c.width = Cm(larguras[j])
            p = c.paragraphs[0]
            if j == 0:
                p.alignment = WD_ALIGN_PARAGRAPH.LEFT
            elif j in dir_cols:
                p.alignment = WD_ALIGN_PARAGRAPH.RIGHT
            else:
                p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            p.paragraph_format.line_spacing = 1.0
            p.paragraph_format.space_after = Pt(2)
            p.paragraph_format.keep_with_next = i != ultima
            fmt(p.add_run(txt))
            _borda(c, ("bottom",) if i == ultima else ())
    f = par(align=WD_ALIGN_PARAGRAPH.JUSTIFY, recuo=0, ls=1.0, depois=0, antes=2)
    fmt(f.add_run(fonte))
    if nota:
        n = par(align=WD_ALIGN_PARAGRAPH.JUSTIFY, recuo=0, ls=1.0, depois=0)
        fmt(n.add_run(nota))
    par(ls=1.0)


def quebra_pagina():
    from docx.enum.text import WD_BREAK
    p = d.add_paragraph()
    p.add_run().add_break(WD_BREAK.PAGE)


# ---------------------------------------------------------------- cabecalho
def ajustar_cabecalho():
    """Substitui apenas o texto do cabecalho, preservando os runs de imagem.

    O logo do programa e o PRIMEIRO run do mesmo paragrafo do texto. Atribuir
    texto a um run que contem um desenho destroi a imagem, entao os runs com
    graphicData sao deixados intactos.
    """
    texto = ("Trabalho de Conclusão de Curso apresentado para obtenção do título de "
             f"especialista em {CURSO} – {ANO}")
    for p in d.sections[0].header.paragraphs:
        if "especialista em" not in p.text:
            continue
        textuais = [r for r in p.runs if "graphicData" not in r._element.xml]
        if not textuais:
            continue
        textuais[0].text = texto
        textuais[0].font.name = ARIAL
        textuais[0].font.size = MIUDA
        textuais[0].font.color.rgb = PRETO
        for r in textuais[1:]:
            r.text = ""
        return


TITULO = ("Arquitetura de microsserviços para distribuição interoperável de "
          "dados de exames em saúde pública")

limpar_corpo()
ajustar_cabecalho()

# ================================================================ FOLHA DE ROSTO
for _ in range(4):
    par(ls=1.0)
p = par(TITULO, align=WD_ALIGN_PARAGRAPH.CENTER, recuo=0, ls=1.0, negrito=True)
par(ls=1.0)
par(ls=1.0)
p = d.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.paragraph_format.line_spacing = 1.0
fmt(p.add_run("Daniel Souza Scremim"))
r = p.add_run("1*"); fmt(r); r.font.superscript = True
fmt(p.add_run("; " + ORIENTADOR))
r = p.add_run("2"); fmt(r); r.font.superscript = True
par(ls=1.0)
e1 = par(align=WD_ALIGN_PARAGRAPH.LEFT, recuo=0, ls=1.0, depois=0)
r = e1.add_run("1*"); fmt(r, tam=MIUDA); r.font.superscript = True
fmt(e1.add_run(" Bacharel em Sistemas de Informação. Autor correspondente: "
               "danielscremim25@gmail.com"), tam=MIUDA)
e2 = par(align=WD_ALIGN_PARAGRAPH.LEFT, recuo=0, ls=1.0, depois=0)
r = e2.add_run("2"); fmt(r, tam=MIUDA); r.font.superscript = True
fmt(e2.add_run(f" {TITULACAO_ORIENTADOR}. Universidade de São Paulo. "
               f"E-mail: {EMAIL_ORIENTADOR}"), tam=MIUDA)
quebra_pagina()

# ================================================================ RESUMO
par(TITULO, align=WD_ALIGN_PARAGRAPH.CENTER, recuo=0, ls=1.0, negrito=True)
par(ls=1.0)
par("Resumo", align=WD_ALIGN_PARAGRAPH.LEFT, recuo=0, ls=1.0, negrito=True)
par(ls=1.0)
par(
    "Os dados clínicos brasileiros permanecem fragmentados em silos institucionais, o que "
    "impede tanto a continuidade do cuidado quanto o uso secundário para vigilância "
    "epidemiológica. Este trabalho propôs e avaliou uma arquitetura de referência de "
    "microsserviços para distribuir resultados de exames entre instituições públicas e "
    "privadas sob o princípio da privacidade por projeto. Implementou-se uma prova de "
    "conceito com dez microsserviços, comunicação assíncrona por fila de eventos, banco "
    "de dados por serviço, malha de serviços com autenticação mútua obrigatória, "
    "verificação de consentimento no caminho crítico da requisição e auditoria imutável. "
    "A avaliação empregou duas máquinas virtuais, isolando o gerador de carga do sistema "
    "sob teste, e mediu desempenho, ponto de ruptura, elasticidade, segurança e "
    "minimização de dados. Com 150 usuários simultâneos a plataforma atendeu aos limites "
    "de projeto, com latência de 48,4 ms no percentil 95 e verificação de consentimento em "
    "18,0 ms. Com 1.000 usuários sustentou 1.324,9 requisições por segundo sem perder "
    "requisição, embora a latência tenha excedido o alvo. A capacidade sustentada foi de "
    "1.400 requisições por segundo e a ruptura ocorreu entre 1.600 e 1.800, causada pela "
    "saturação do conjunto de conexões de banco e não pelo processamento. Reduzir o limite "
    "do autoescalador de dez para seis réplicas multiplicou a vazão por 9,4. A camada de "
    "leitura declarativa reduziu em 51,98% os dados trafegados. Concluiu-se que a "
    "arquitetura sustenta a distribuição interoperável com consentimento verificado, e que "
    "o dimensionamento do autoescalador ao substrato é determinante para o desempenho.",
    align=WD_ALIGN_PARAGRAPH.JUSTIFY, recuo=0, ls=1.0)
par(ls=1.0)
pk = par(align=WD_ALIGN_PARAGRAPH.JUSTIFY, recuo=0, ls=1.0)
fmt(pk.add_run("Palavras-chave: "), negrito=True)
fmt(pk.add_run("proteção de dados; Kubernetes; consentimento; escalabilidade; "
               "observabilidade."))
quebra_pagina()

# ================================================================ INTRODUCAO
titulo_secao("Introdução")
par()
par("A informação clínica produzida no Brasil encontra-se dispersa entre unidades básicas "
    "de saúde, hospitais públicos e privados e laboratórios, cada qual mantendo bases, "
    "formatos e mecanismos de autenticação próprios. A consequência prática é conhecida: "
    "o paciente transporta documentos em papel entre instituições, exames são repetidos "
    "por indisponibilidade do histórico e a vigilância epidemiológica enxerga fragmentos "
    "em vez do conjunto. O obstáculo não é a ausência de algoritmos de análise, "
    "abundantes na literatura, nem exclusivamente a regulamentação, mas a falta de uma "
    "camada de distribuição que combine interoperabilidade, escala e controle efetivo de "
    "privacidade.")
par("A Lei Geral de Proteção de Dados Pessoais [LGPD] estabelece que dados de saúde "
    "constituem categoria sensível e consagra a minimização como princípio, determinando "
    "que o tratamento se limite ao mínimo necessário à finalidade declarada (Brasil, "
    "2018). Esse comando tem consequência arquitetural direta: não basta autenticar o "
    "consumidor, é preciso verificar consentimento no caminho crítico de cada acesso, "
    "registrar de forma imutável quem acessou o quê e com qual finalidade, e transmitir "
    "apenas os campos efetivamente requeridos.")
par("No âmbito federal, a Rede Nacional de Dados em Saúde [RNDS] opera desde 2020 com "
    "base no padrão HL7 FHIR R4 e autenticação por certificado digital. Seu escopo de "
    "exames laboratoriais, contudo, restringe-se a dois patógenos associados a emergências "
    "sanitárias internacionais, permanecendo a rotina clínica ambulatorial, os exames de "
    "imagem e as doenças de notificação compulsória em planos sem cronograma público de "
    "implementação (Brasil, 2022). Existe, portanto, um espaço técnico legítimo para "
    "investigar como estruturar a distribuição desse universo mais amplo de exames, com "
    "tratamento uniforme entre produtores públicos e privados.")
par("A arquitetura de microsserviços oferece o particionamento por responsabilidade e a "
    "escalabilidade independente que esse problema demanda, ao custo de complexidade "
    "operacional e de consistência distribuída (Newman, 2021; Richardson, 2019). A "
    "comunicação assíncrona por eventos, por sua vez, desacopla produtores de consumidores "
    "e permite que a ingestão prossiga enquanto o processamento acumula fila, propriedade "
    "relevante quando a rede de origem dos dados é heterogênea e sujeita a picos "
    "(Kleppmann, 2017). Resta, entretanto, a questão empírica: em que medida tais "
    "propriedades se sustentam quando se acrescenta verificação de consentimento no "
    "caminho crítico, criptografia mútua entre todos os serviços e auditoria de cada "
    "acesso, e onde está o limite dessa composição.")
par("Este trabalho teve por objetivo propor e avaliar experimentalmente uma arquitetura "
    "de referência de microsserviços para a distribuição interoperável de dados de exames "
    "na rede de saúde, sob privacidade por projeto, determinando sua capacidade "
    "sustentada, seu ponto de ruptura e a causa da saturação, bem como o custo e o "
    "benefício dos mecanismos de consentimento, auditoria e minimização de dados.")

# As secoes restantes ficam em arquivo separado apenas por tamanho; executadas
# aqui para compartilhar o documento e as funcoes de formatacao ja definidas.
exec(open(os.path.join(BASE, "gerar-tcc-corpo.py"), encoding="utf-8").read())
