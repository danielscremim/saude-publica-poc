# -*- coding: utf-8 -*-
# Continuacao de gerar-tcc.py: Metodologia, Resultados, Conclusao e Referencias.
# Executado por exec() a partir daquele arquivo, compartilhando o documento e as
# funcoes de formatacao.

# ================================================================ METODOLOGIA
titulo_secao("Metodologia")
par()
par("A pesquisa adotou delineamento experimental aplicado, organizado em duas etapas: a "
    "construção de uma prova de conceito funcional da arquitetura proposta e a avaliação "
    "dessa implementação sob carga controlada, em infraestrutura de datacenter. Todo o "
    "código, os manifestos de implantação, os roteiros de carga e os dados brutos das "
    "medições foram versionados em repositório público, de modo a permitir a reprodução "
    "integral do experimento.")

subtitulo("Arquitetura da plataforma")
par("A plataforma foi decomposta em dez microsserviços, agrupados em quatro domínios: "
    "identidade e acesso, gestão clínica, laboratório e integração. Cada serviço manteve "
    "banco de dados próprio, em conformidade com o padrão de persistência por serviço, e "
    "expôs contrato REST versionado com especificação aberta. A comunicação entre "
    "domínios ocorreu de forma assíncrona, por meio de quatro tópicos de eventos, com o "
    "identificador do paciente empregado como chave de particionamento para garantir "
    "ordenação por titular.")
par("Três decisões concretizaram o princípio da privacidade por projeto. A primeira foi a "
    "tokenização do número de cadastro de pessoa física: o identificador nacional existe "
    "apenas no serviço de cadastro, e todos os demais serviços, eventos e respostas de "
    "interface trafegam um identificador opaco. A segunda foi a verificação de "
    "consentimento no caminho crítico: toda leitura de dados por instituição externa "
    "consulta o serviço de consentimento antes de qualquer agregação, e a ausência de "
    "consentimento ativo resulta em recusa. A terceira foi a auditoria imutável: cada "
    "acesso, autorizado ou negado, gera evento consumido por serviço que apenas insere "
    "registros, sem operações de alteração ou exclusão, e que aplica detecção automática "
    "de anomalia por janela deslizante de acessos.")
par("A camada de leitura foi implementada como fachada agregadora com consulta "
    "declarativa, em que o consumidor especifica os campos desejados e os agregados não "
    "solicitados não geram chamada aos serviços de origem. A escrita permaneceu em "
    "interface REST, caracterizando separação entre os caminhos de leitura e de escrita. "
    "A autorização permaneceu no serviço de aplicação, e não na camada de resolução da "
    "consulta, de modo que ambos os caminhos compartilham o mesmo controle.")
par("A implantação ocorreu em Kubernetes, com malha de serviços configurada para exigir "
    "autenticação mútua entre todos os pares, disjuntor de circuito por destino e "
    "autoescalador horizontal acionado a 70% de utilização de processador. Um gateway de "
    "aplicação em modo declarativo concentrou a entrada norte-sul.")

subtitulo("Ambiente experimental")
par("O experimento foi conduzido em duas máquinas virtuais de datacenter, cuja "
    "configuração consta da Tabela 1. A separação entre o sistema sob teste e o gerador "
    "de carga não foi apenas precaução metodológica: em execução preliminar, na qual o "
    "gerador acessava o cluster por encaminhamento de porta, o canal de medição "
    "acrescentou cerca de 300 ms por requisição e reprovou três dos quatro limites "
    "estabelecidos, com o sistema íntegro. O episódio ilustra que instrumentação "
    "intrusiva pode dominar o fenômeno medido.")

tabela(1, "Configuração das máquinas virtuais empregadas no experimento",
       ["Item", "Sistema sob teste", "Gerador de carga"],
       [["Processadores lógicos", "32", "8"],
        ["Memória", "62 GB", "15 GB"],
        ["Sistema operacional", "Ubuntu Server 24.04", "Ubuntu Server 24.04"],
        ["Orquestração", "Kubernetes 1.35.5", "não aplicável"],
        ["Malha de serviços", "Istio 1.30.4", "não aplicável"],
        ["Ferramenta de carga", "não aplicável", "k6 2.2.0"]],
       [5.5, 5.2, 5.2],
       fonte="Fonte: Dados originais da pesquisa",
       nota="Nota: latência de rede entre as máquinas de 0,218 ms em média, sem perda de pacotes")

par("Cada rodada foi precedida de reposição completa do estado: truncamento das tabelas, "
    "retorno ao número mínimo de réplicas e recriação dos processos. Essa disciplina "
    "decorreu de observação da primeira bateria, em que as rodadas não eram independentes. "
    "A janela padrão de estabilização do autoescalador para reduzir réplicas é de 300 s, "
    "enquanto a pausa entre rodadas era de 180 s, de modo que cada rodada iniciava mais "
    "escalada que a anterior; somava-se a isso o crescimento do volume de dados, já que o "
    "cenário executa 20% de operações de escrita.")

subtitulo("Cenários de avaliação")
par("Foram definidos cinco cenários, descritos na Tabela 2, cujos nomes orientam também a "
    "apresentação dos resultados. A distinção entre os dois primeiros e o terceiro merece "
    "registro. Os cenários de carga de referência e nominal empregaram usuários virtuais, "
    "em que cada usuário aguarda a resposta antes da requisição seguinte; nessa "
    "formulação a carga se autorregula e o limite real permanece mascarado. O cenário de "
    "ponto de ruptura empregou taxa de chegada fixa, em degraus de 200 requisições por "
    "segundo, independentemente do tempo de resposta, o que permite afirmar a capacidade "
    "em termos absolutos.")

tabela(2, "Cenários de avaliação, respectivos parâmetros e requisitos atendidos",
       ["Cenário", "Parâmetros", "Objetivo"],
       [["Carga de referência", "150 usuários virtuais", "Verificar os limites de projeto com folga"],
        ["Carga nominal", "1.000 usuários virtuais", "Verificar o requisito de escalabilidade"],
        ["Ponto de ruptura", "200 a 2.000 req/s em degraus", "Determinar a capacidade e a causa da saturação"],
        ["Resiliência", "Falha provocada sob carga", "Medir o tempo de reposição e o erro percebido"],
        ["Minimização de dados", "200 usuários virtuais", "Quantificar a redução de dados trafegados"]],
       [4.3, 4.8, 6.8],
       fonte="Fonte: Dados originais da pesquisa",
       nota="Nota: cada cenário foi executado em três rodadas, exceto o ponto de ruptura, "
            "executado em duas repetições independentes")

par("O critério de ruptura adotado considerou o primeiro degrau em que ocorresse taxa de "
    "erro superior a 1%, latência no percentil 95 acima de 2.000 ms, descarte de "
    "iterações pelo gerador ou presença de contêineres em estado de falha. O cenário de "
    "resiliência consistiu na remoção deliberada de uma réplica íntegra durante carga "
    "nominal, com registro dos instantes de criação e de prontidão do substituto.")

subtitulo("Instrumentação e tratamento dos dados")
par("A coleta combinou duas fontes independentes. Do lado do cliente, o gerador de carga "
    "registrou latência por percentil, vazão, taxa de erro e volume de dados por resposta, "
    "segregados por tipo de operação. Do lado do servidor, um coletor amostrou a cada 5 s "
    "o consumo de processador do nó e por contêiner, o número de réplicas, o conjunto de "
    "conexões de banco, as threads do servidor de aplicação, a fila de eventos por "
    "consumir e o número de registros persistidos.")
par("A instrumentação exigiu correções que merecem registro por afetarem a validade das "
    "medições. A biblioteca de exportação de métricas não constava das dependências, de "
    "modo que o ponto de coleta declarado na configuração não existia; o seletor de porta "
    "do monitor de coleta não gerava alvo, e o registro de beans de gerenciamento do "
    "servidor de aplicação estava desabilitado por padrão. Os três defeitos tinham a mesma "
    "assinatura: ausência de dado sem mensagem de erro. Instrumento que falha em silêncio "
    "é mais perigoso que instrumento que falha de forma visível, e a validação do "
    "instrumento foi tratada como parte do método.")
par("Os resultados foram reportados como média e desvio padrão de três rodadas, com o "
    "aquecimento descartado. Para a série temporal de contagem de registros empregou-se a "
    "estimativa do coletor de estatísticas do banco, e não a contagem exata, a fim de não "
    "perturbar o experimento; ao final, a comparação com a contagem exata apresentou "
    "divergência de 0,012%, o que confirmou a adequação do instrumento.")

exec(open(os.path.join(BASE, "gerar-tcc-resultados.py"), encoding="utf-8").read())
