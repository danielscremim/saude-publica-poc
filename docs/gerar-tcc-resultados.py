# -*- coding: utf-8 -*-
# Resultados e Discussao, Conclusao, Agradecimentos e Referencias.

titulo_secao("Resultados e Discussão")
par()
par("A apresentação segue a ordem dos cenários definidos na Metodologia. Os valores "
    "correspondem à média de três rodadas, com o desvio padrão indicado, salvo quando "
    "explicitado de outro modo.")

# ---------------------------------------------------------------- 1
subtitulo("Carga de referência e carga nominal")
par("Com 150 usuários simultâneos a plataforma atendeu aos limites de projeto, e não "
    "apenas aos limites relaxados adotados para a prova de conceito, conforme a Tabela 3. "
    "A verificação de consentimento respondeu em 18,0 ms no percentil 95, valor que "
    "corresponde a uma checagem executada no caminho crítico da requisição, atravessando "
    "gateway, proxy lateral e criptografia mútua, e não a uma consulta direta ao banco. "
    "Registre-se que a terceira rodada mediu 21,07 ms nessa métrica e excedeu o teto de "
    "20 ms; reportar apenas a média ocultaria essa dispersão.")

tabela(3, "Desempenho nos cenários de carga de referência e de carga nominal",
       ["Métrica", "150 usuários", "1.000 usuários", "Limite de projeto"],
       [["Latência geral, percentil 95 (ms)", "48,4 ± 7,7", "818 ± 110", "500"],
        ["Linha do tempo clínica, percentil 95 (ms)", "68,1 ± 11,2", "937 ± 142", "800"],
        ["Consentimento, percentil 95 (ms)", "18,0 ± 3,2", "398 ± 73", "20"],
        ["Taxa de erro (%)", "0,00 a 0,01", "0,00", "menor que 1"],
        ["Vazão sustentada (req/s)", "428,1 ± 5,8", "1.324,9 ± 75,0", "não definido"]],
       [6.6, 3.1, 3.5, 2.8], dir_cols=(1, 2, 3))

par("Sob 1.000 usuários simultâneos a plataforma sustentou a carga sem perder uma única "
    "requisição nas três rodadas, com vazão de 1.324,9 ± 75,0 requisições por segundo. A "
    "latência, entretanto, excedeu o alvo de projeto. A evidência indica saturação de "
    "recurso compartilhado, e não defeito de desenho: os três indicadores degradaram por "
    "fatores semelhantes, entre 17 e 26 vezes, entre um cenário e outro. Uma consulta "
    "ineficiente ou índice ausente teria degradado um deles de forma desproporcional. O "
    "comportamento é compatível com o previsto pela teoria de filas para utilização "
    "próxima da saturação, em que o tempo de resposta cresce de forma não linear "
    "(Kleppmann, 2017).")

par("A investigação dessa saturação produziu o resultado mais contraintuitivo do "
    "trabalho. Ao remover uma restrição do sistema operacional que limitava a criação de "
    "proxies laterais, o autoescalador passou a elevar efetivamente o número de réplicas, "
    "e o desempenho piorou de forma drástica. A Tabela 4 e a Figura 2 comparam as três "
    "configurações observadas.")

tabela(4, "Efeito do dimensionamento do autoescalador sobre o desempenho",
       ["Configuração", "Réplicas ativas", "Vazão", "Percentil 95", "Erro", "Req/s por núcleo"],
       [["Limite de dez réplicas", "57", "130 req/s", "38,3 s", "22,8%", "4,2"],
        ["Limite de seis réplicas", "45", "1.225 req/s", "918 ms", "0,00%", "40,7"],
        ["Bateria inicial", "43", "1.325 req/s", "818 ms", "0,00%", "47,2"]],
       [4.0, 2.4, 2.6, 2.4, 1.9, 2.7], dir_cols=(1, 2, 3, 4, 5),
       nota="Nota: a razão entre vazão e utilização de processador do nó expressa o "
            "trabalho útil realizado por núcleo")

figura("fig6-eficiencia-por-nucleo.png", 2,
       "Requisições atendidas por segundo por núcleo de processador nas três "
       "configurações do autoescalador",
       largura=11.5)

par("Com praticamente a mesma utilização de processador, 98% contra 94%, a configuração "
    "com menos réplicas realizou 9,7 vezes mais trabalho útil por núcleo. A explicação é "
    "que cada réplica adicional carrega uma máquina virtual Java e um proxy lateral, "
    "consumindo capacidade sem acrescentar nenhuma. Acima do que o nó comporta, o "
    "autoescalador entra em realimentação positiva: observa utilização elevada, solicita "
    "mais réplicas e estas consomem mais processador. Note-se que o valor nominal do "
    "limite não é o fator determinante, e sim o número de réplicas que efetivamente "
    "executam: as duas configurações saudáveis operaram com 43 e 45 réplicas, e a "
    "degradada com 57. O autoescalador precisa, portanto, ser dimensionado ao substrato, "
    "e não configurado no máximo por padrão.")

# ---------------------------------------------------------------- 2
subtitulo("Ponto de ruptura")
par("A Figura 3 apresenta a curva de saturação obtida em duas execuções independentes. "
    "As curvas coincidem dentro de poucos milissegundos até 1.200 requisições por "
    "segundo; a divergência posterior é esperada, pois nas proximidades da saturação "
    "pequenas diferenças de estado se amplificam.")

figura("fig1-curva-saturacao.png", 3,
       "Latência no percentil 95 em função da taxa de chegada, em duas execuções "
       "independentes, com o limite adotado para a prova de conceito assinalado")

par("A capacidade sustentada foi de 1.400 requisições por segundo, último degrau com "
    "baixa dispersão, erro nulo e gerador de carga ainda folgado. A ruptura, pelo critério "
    "de latência, situou-se entre 1.600 e 1.800 requisições por segundo. Importa observar "
    "que a degradação foi de latência e não de disponibilidade: mesmo a 2.000 requisições "
    "por segundo, com latência de quatro segundos, a taxa de erro máxima foi de 0,17%. A "
    "plataforma enfileirou em vez de recusar, comportamento característico de sistema com "
    "contrapressão. Ressalve-se que, acima de 1.600 requisições por segundo, o próprio "
    "gerador atingiu seu limite, com 83.873 e 67.969 iterações descartadas nas duas "
    "execuções, de modo que os degraus superiores constituem limite inferior da latência "
    "real.")
par("A causa da saturação foi isolada e contraria a expectativa usual. A Figura 4 "
    "apresenta, no mesmo eixo de carga e em painéis separados, a latência e o número de "
    "requisições aguardando conexão de banco.")

figura("fig2-fila-conexoes.png", 4,
       "Latência no percentil 95 e requisições aguardando conexão de banco de dados, "
       "em função da taxa de chegada")

par("Até 1.200 requisições por segundo não houve qualquer fila por conexão. A primeira "
    "fila surgiu 27 segundos após o início do degrau de 1.400 requisições por segundo, e "
    "a latência acompanhou na mesma cadência. No ponto de ruptura, os conjuntos de "
    "conexões dos serviços de cadastro e de consentimento estavam integralmente ocupados, "
    "com cinco conexões em uso de cinco disponíveis, e 46 e 41 requisições aguardando, "
    "respectivamente. As threads do servidor de aplicação, por sua vez, operavam a menos "
    "de um terço do limite, com máximo de 64 threads ocupadas de 200 configuradas, e a "
    "utilização de processador do nó subiu apenas de 74% para 82%. As threads não estavam "
    "trabalhando: estavam bloqueadas aguardando conexão de banco.")
par("Esse limite é ajustável e essa constatação qualifica o resultado. O orçamento "
    "vigente consome 240 das 500 conexões configuradas no servidor de banco, considerando "
    "oito serviços com persistência, seis réplicas e cinco conexões por instância. Dobrar "
    "o conjunto de conexões caberia no teto existente e deslocaria o ponto de ruptura para "
    "cima. Conclui-se que a capacidade medida não é propriedade intrínseca da arquitetura, "
    "mas consequência de uma decisão de dimensionamento documentada.")

# ---------------------------------------------------------------- 3
subtitulo("Desacoplamento assíncrono")
par("O comportamento da fila de eventos forneceu a evidência mais direta do "
    "desacoplamento proporcionado pela comunicação assíncrona. A Figura 5 apresenta o "
    "acúmulo de registros de auditoria persistidos, com o instante de encerramento da "
    "carga assinalado.")

figura("fig4-drenagem-kafka.png", 5,
       "Registros de auditoria persistidos ao longo do tempo, com o instante de "
       "encerramento da carga assinalado")

par("No auge, a fila acumulou 1.385.322 eventos por consumir. Após o encerramento da "
    "carga, aproximadamente 1,40 milhão de eventos foram processados ao longo de 26 "
    "minutos e 24 segundos, a uma taxa sustentada próxima de 940 eventos por segundo, sem "
    "perda de um único evento e com taxa de erro nula durante todo o período. O valor foi "
    "obtido por dois instrumentos independentes, a fila reportada pelo consumidor e a "
    "contagem de registros no banco, que convergiram com divergência inferior a 1%.")
par("A interpretação é que a plataforma continuou aceitando requisições no ritmo do "
    "cliente enquanto o processamento assíncrono acumulava fila, drenando-a em seguida. "
    "Uma composição síncrona teria propagado essa pressão de volta ao cliente, na forma de "
    "latência crescente ou recusa, exatamente o efeito que a mediação por eventos evita "
    "(Kleppmann, 2017). Registre-se ainda que o gargalo foi localizado: todos os demais "
    "consumidores mantiveram fila nula durante toda a bateria, e apenas o serviço de "
    "auditoria acumulou, por executar contagem de janela deslizante a cada inserção. "
    "Trata-se de otimização pontual, que não altera o desenho da arquitetura.")

# ---------------------------------------------------------------- 4
subtitulo("Elasticidade e resiliência")
par("Quatro serviços atingiram o número máximo de réplicas, precisamente aqueles do "
    "caminho crítico do cenário, e os mesmos quatro de execução anterior realizada em "
    "substrato distinto, o que sugere que o comportamento decorre do desenho e não da "
    "infraestrutura. A Figura 6 apresenta a evolução do número de réplicas.")

figura("fig3-replicas-hpa.png", 6,
       "Número de réplicas ativas dos quatro serviços do caminho crítico ao longo da "
       "bateria de carga")

par("O tempo de reação foi de aproximadamente dois minutos entre a primeira elevação e o "
    "número máximo de réplicas, limitado pela política padrão de crescimento do "
    "autoescalador. Observou-se que o escalonamento ao máximo ocorreu já no cenário de "
    "referência, com 428 requisições por segundo, pois o gatilho de 70% de utilização "
    "sobre apenas duas réplicas é atingido com folga nessa carga.")
par("No cenário de resiliência, a remoção deliberada de uma réplica íntegra sob carga "
    "nominal produziu tempo de reposição de 42 s e 43 s em duas execuções, acima do alvo "
    "de 30 s estabelecido. A decomposição do intervalo esclarece onde o tempo é "
    "consumido: o orquestrador detectou a falha e criou o substituto em até dois segundos; "
    "o agendamento e a inicialização do proxy lateral consumiram cerca de nove segundos; e "
    "a inicialização da máquina virtual Java consumiu 28,5 s, valor medido diretamente no "
    "registro da aplicação. Sob carga a inicialização levou 28,5 s, contra "
    "aproximadamente 20 s sem carga, o que evidencia o efeito da disputa por processador.")
par("Uma hipótese foi formulada e refutada nesse ponto. Atribuiu-se inicialmente o tempo "
    "à configuração da sonda de prontidão, que impunha atraso inicial de 30 s. A "
    "substituição por sonda de inicialização, sem atraso, levou o tempo de 42 s para 43 s, "
    "ou seja, não o alterou. A restrição real era a inicialização da máquina virtual, cuja "
    "duração coincidia com o atraso configurado. A alteração foi mantida por correção "
    "técnica, sem que se lhe atribua ganho.")
par("Há, contudo, distinção essencial entre dois tempos. O tempo de reposição da réplica "
    "foi de 43 s, acima do alvo. A indisponibilidade percebida pelo cliente, entretanto, "
    "foi praticamente nula: a primeira execução registrou 0,00% de erro e a segunda "
    "0,01%, correspondendo a 66 requisições em 609.846. Confrontado com o próprio "
    "requisito, que estabelece disponibilidade de 99,9% e portanto tolera 0,100% de falha, "
    "o valor medido de 0,011% situa-se uma ordem de grandeza abaixo do tolerado. O que se "
    "degradou por 43 s foi a redundância, não a disponibilidade. Isso sugere que o "
    "requisito, como formulado, mede o tempo de reposição de uma réplica quando o que "
    "declara proteger é a disponibilidade, e as duas grandezas divergem quando há "
    "redundância suficiente.")

# ---------------------------------------------------------------- 5
subtitulo("Segurança e consentimento")
par("A exigência de autenticação mútua entre todos os pares foi verificada de forma "
    "direta. Um contêiner sem proxy lateral, e portanto sem identidade na malha, obteve "
    "recusa de conexão ao tentar alcançar um serviço interno, enquanto contêiner "
    "equivalente com proxy lateral obteve resposta normal. O tráfego sem identidade é "
    "rejeitado na camada de transporte, antes de alcançar a aplicação.")
par("A topologia efetivamente em execução foi verificada pela reconstrução "
    "arquitetural dinâmica descrita na Metodologia. O grafo de dependências derivado "
    "das chamadas interceptadas pelos proxies laterais durante os cenários de carga "
    "coincidiu com o projetado, sem arestas inesperadas entre serviços e sem acesso "
    "de um serviço ao banco de dados de outro, o que confirma a persistência por "
    "serviço no sistema em execução e não apenas no código (Cerny et al., 2022).")
par("O controle de acesso à linha do tempo clínica foi verificado nos quatro estados "
    "possíveis. Requisição sem credencial recebeu recusa por ausência de autenticação; "
    "requisição autenticada sem consentimento ativo recebeu recusa por ausência de "
    "consentimento, com registro de auditoria da tentativa negada; requisição autenticada "
    "com consentimento ativo obteve os dados, com registro do acesso autorizado; e, após a "
    "revogação do consentimento, nova requisição foi novamente recusada, com novo "
    "registro. Nenhum acesso externo ocorreu sem consentimento ativo, e toda tentativa, "
    "autorizada ou negada, foi registrada.")
par("O fluxo de ingestão por instituição externa observou o mesmo rigor. Requisição sem "
    "credencial e requisição com credencial de escopo incorreto foram recusadas, e apenas "
    "a portadora do escopo de escrita obteve registro do resultado, com propagação "
    "subsequente para notificação e auditoria. Cabe destacar que o produtor privado "
    "utilizou exatamente o mesmo contrato e o mesmo controle de escopo aplicados aos "
    "produtores públicos, o que concretiza o tratamento uniforme entre os dois lados da "
    "rede.")
par("A revogação do consentimento tornou-se efetiva em 297 ms, contra o teto de 1.000 ms "
    "estabelecido. A detecção de anomalia operou de forma automática e sem duplicação, "
    "registrando 29 alertas ao longo de 3.399.128 registros de auditoria acumulados. O "
    "resultado materializa a observabilidade aplicada à segurança: o mesmo registro que "
    "serve à auditoria de conformidade opera como sinal de acesso anômalo, sem "
    "instrumentação adicional (Ramachandran, 2024). Por "
    "fim, o número de cadastro de pessoa física não apareceu em qualquer resposta de "
    "interface, registro de aplicação ou evento, circulando exclusivamente o identificador "
    "opaco, em conformidade com o princípio da minimização (Brasil, 2018).")

# ---------------------------------------------------------------- 6
subtitulo("Minimização de dados")
par("A comparação entre a resposta completa e fixa da interface REST e a consulta "
    "declarativa que especifica apenas os campos necessários à finalidade consta da "
    "Figura 7. As medições mostraram-se notavelmente estáveis ao longo das três rodadas.")

figura("fig5-bytes-rest-graphql.png", 7,
       "Volume de dados por resposta na interface REST e na consulta declarativa, "
       "média de três rodadas",
       largura=8.8)

par("A redução foi de 51,98%, de 5.522,3 ± 0,3 bytes para 2.651,7 ± 0,1 bytes por "
    "resposta. Adicionalmente, a visão consolidada de quatro domínios foi obtida em uma "
    "única requisição, contra quatro na composição REST equivalente, reduzindo os pontos "
    "de integração que o consumidor precisa conhecer. O fundamento do ganho é normativo "
    "antes de ser técnico: o campo não declarado simplesmente não é transmitido, o que "
    "materializa a limitação do tratamento ao mínimo necessário à finalidade declarada "
    "(Brasil, 2018).")
par("Cabe, entretanto, ressalva que delimita o alcance do resultado. A minimização não "
    "reduziu a latência: a consulta declarativa apresentou 187 ± 5 ms contra 205 ± 51 ms "
    "da linha de base, valores estatisticamente equivalentes. Em rede local com latência "
    "de 0,2 ms, economizar 2,9 kB não compensa o custo de interpretação e validação da "
    "consulta. O benefício verificado é de conformidade legal e de acoplamento, não de "
    "desempenho, e reivindicá-lo de outra forma seria indefensável.")

# ---------------------------------------------------------------- 7
subtitulo("Síntese e limitações")
par("A Tabela 5 confronta os resultados obtidos com os requisitos não funcionais "
    "estabelecidos para a plataforma.")

tabela(5, "Confronto dos resultados obtidos com os requisitos não funcionais estabelecidos",
       ["Requisito", "Situação", "Evidência"],
       [["Escalabilidade", "Parcial",
         "1.000 usuários com 0,00% de erro e 1.325 req/s; percentil 95 de 818 ms contra alvo de 500 ms"],
        ["Disponibilidade", "Parcial",
         "Disponibilidade de 99,989% durante falha provocada; reposição em 43 s contra alvo de 30 s"],
        ["Segurança e proteção de dados", "Atendido",
         "Autenticação mútua verificada; auditoria imutável; identificador nacional ausente de todas as saídas"],
        ["Observabilidade", "Parcial",
         "Métricas de aplicação e de malha coletadas e três alertas ativos; rastreamento fim a fim não verificado"],
        ["Interoperabilidade", "Atendido",
         "Especificação aberta nos nove serviços com interface REST; linha do tempo em 68,1 ms"],
        ["Consentimento", "Parcial",
         "Verificação em 18,0 ms e revogação em 297 ms; limite de requisições por titular não implementado"]],
       [4.2, 2.3, 9.5])

par("Cinco limitações delimitam a validade dos resultados e devem acompanhar qualquer "
    "generalização. Primeiro, o experimento utilizou nó único, sem escalonamento de nós, "
    "de modo que a arquitetura escala horizontalmente apenas até o limite do substrato; "
    "acima disso o autoescalador solicita réplicas que não há onde acomodar, e esse "
    "comportamento constitui ele próprio um resultado. Segundo, a carga sintética "
    "concentrou as leituras em poucos titulares, mantendo a memória intermediária do banco "
    "aquecida, o que torna as latências um teto otimista. Terceiro, acima de 1.600 "
    "requisições por segundo o gerador de carga também saturou, e os degraus superiores "
    "devem ser lidos como limite inferior. Quarto, o limite de requisições por titular não "
    "foi implementado: o mecanismo de detecção foi demonstrado, mas a variante bloqueante "
    "permanece como decisão de política, e exercitá-la exigiria modelo de carga com "
    "cardinalidade realista de titulares. Quinto, o rastreamento distribuído não foi "
    "verificado de ponta a ponta, pois os proxies laterais geram os registros mas não há "
    "propagação explícita de cabeçalhos no código da aplicação; sem o identificador de "
    "correlação propagado, a reconstrução do percurso completo de uma requisição "
    "permanece incompleta (Cerny et al., 2022).")
par("Permanecem identificadas, e não implementadas, quatro otimizações com efeito "
    "esperado sobre os limites medidos: paralelizar as duas chamadas independentes do "
    "agregador; armazenar em memória intermediária a decisão de consentimento, cuja "
    "invalidação por evento já está prevista na arquitetura por tópico existente e ainda "
    "sem consumidor; elevar o conjunto de conexões de banco dentro do teto disponível; e "
    "adotar compilação antecipada para reduzir o tempo de inicialização da máquina "
    "virtual, que responde por dois terços do tempo de reposição de réplicas.")

# ================================================================ CONCLUSAO
titulo_secao("Conclusão")
par()
par("A arquitetura de microsserviços proposta mostrou-se capaz de distribuir dados de "
    "exames entre instituições públicas e privadas mantendo, no caminho crítico de cada "
    "requisição, a verificação de consentimento do titular, a criptografia mútua entre "
    "todos os serviços e o registro imutável de cada acesso. A capacidade sustentada foi "
    "de 1.400 requisições por segundo, com ruptura entre 1.600 e 1.800, e sob 1.000 "
    "usuários simultâneos a plataforma operou sem perder uma única requisição.")
par("O custo dos mecanismos de privacidade mostrou-se compatível com a operação: a "
    "verificação de consentimento no caminho crítico respondeu em 18,0 ms sob carga de "
    "referência, e a revogação tornou-se efetiva em 297 ms. A camada de leitura "
    "declarativa reduziu em 51,98% o volume de dados trafegados, materializando o "
    "princípio da minimização, embora sem efeito sobre a latência, o que delimita o "
    "benefício ao campo da conformidade e do acoplamento.")
par("Três achados de engenharia transcendem o caso estudado. O primeiro é que o "
    "autoescalador precisa ser dimensionado ao substrato: elevar o limite de réplicas "
    "acima do que o nó comporta degradou o desempenho por um fator próximo de dez, pois "
    "cada réplica adicional consome capacidade sem acrescentar nenhuma. O segundo é que a "
    "saturação ocorreu no conjunto de conexões de banco, e não no processamento nem nas "
    "threads do servidor, o que torna o limite medido consequência de uma decisão de "
    "dimensionamento ajustável e não propriedade intrínseca do desenho. O terceiro é que o "
    "tempo de reposição de réplicas é dominado pela inicialização da máquina virtual, e "
    "não pela reação do orquestrador, que respondeu em menos de dois segundos.")
par("Verificou-se, ainda, divergência entre o que o requisito de disponibilidade mede e o "
    "que declara proteger: a reposição de réplica excedeu o alvo estabelecido, ao passo "
    "que a disponibilidade percebida pelo cliente permaneceu uma ordem de grandeza acima "
    "do exigido. Em arquiteturas com redundância suficiente, formular o requisito em "
    "termos de erro percebido mostra-se mais aderente ao que se pretende garantir.")
par("A contribuição do trabalho é arquitetural e metodológica, não uma promessa de "
    "capacidade absoluta em produção. Os valores obtidos valem para a configuração de "
    "hardware empregada e para o modelo de carga descrito. Como desdobramento, indicam-se "
    "a implementação do limite de requisições por titular, a propagação explícita de "
    "rastreamento entre serviços, a adoção de armazenamento intermediário para a decisão "
    "de consentimento com invalidação por evento e a avaliação da mesma arquitetura em "
    "cluster com múltiplos nós, condição em que o comportamento do autoescalador poderá "
    "ser observado sem o teto imposto pelo substrato único.")

# ================================================================ AGRADECIMENTOS
titulo_secao("Agradecimentos")
par()
par("Ao orientador, pela condução criteriosa do trabalho, e à empresa que disponibilizou "
    "a infraestrutura de datacenter utilizada nos experimentos, sem a qual a avaliação "
    "sob carga não teria sido possível.")

# ================================================================ REFERENCIAS
titulo_secao("Referências")
par()
refs = [
    "Brasil. 2018. Lei nº 13.709, de 14 de agosto de 2018. Dispõe sobre a proteção de "
    "dados pessoais e altera a Lei nº 12.965, de 23 de abril de 2014 (Marco Civil da "
    "Internet). Diário Oficial da União, Brasília, 15 ago. 2018. Seção 1, p. 59.",

    "Brasil. 2022. Portaria GM/MS nº 883, de 7 de dezembro de 2022. Institui a Estratégia "
    "de Saúde Digital para o Brasil e dispõe sobre a Rede Nacional de Dados em Saúde. "
    "Diário Oficial da União, Brasília, 09 dez. 2022. Seção 1, p. 143.",

    "Cerny, T.; Abdelfattah, A.S.; Bushong, V.; Maruf, A.A.; Taibi, D. 2022. "
    "Microservice architecture reconstruction and visualization techniques: a "
    "review. arXiv:2207.02988v2. Disponível em: "
    "<https://arxiv.org/abs/2207.02988>. Acesso em: 21 set. 2026.",

    "Cloud Native Computing Foundation [CNCF]. 2025. Kubernetes Documentation: Horizontal "
    "Pod Autoscaling. Disponível em: "
    "<https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/>. "
    "Acesso em: 20 set. 2026.",

    "Health Level Seven International [HL7]. 2019. HL7 Fast Healthcare Interoperability "
    "Resources Specification (FHIR), Release 4. Disponível em: "
    "<https://www.hl7.org/fhir/R4/>. Acesso em: 19 set. 2026.",

    "Istio Authors. 2025. Istio Documentation: Security. Disponível em: "
    "<https://istio.io/latest/docs/concepts/security/>. Acesso em: 20 set. 2026.",

    "Kleppmann, M. 2017. Designing Data-Intensive Applications. 1ed. O'Reilly Media, "
    "Sebastopol, CA, EUA.",

    "Ministério da Saúde [MS]. 2024. Modelo de Informação de Resultado de Exame "
    "Laboratorial da Rede Nacional de Dados em Saúde. Disponível em: "
    "<https://rnds-guia.saude.gov.br/docs/rel/mi-rel/>. Acesso em: 19 set. 2026.",

    "Ministério da Saúde [MS]. 2025. Rede Nacional de Dados em Saúde. Disponível em: "
    "<https://www.gov.br/saude/pt-br/composicao/seidigi/rnds>. Acesso em: 19 set. 2026.",

    "Newman, S. 2021. Building Microservices: Designing Fine-Grained Systems. 2ed. "
    "O'Reilly Media, Sebastopol, CA, EUA.",

    "Ramachandran, R. 2024. Leveraging security observability to strengthen "
    "security of digital ecosystem architecture. arXiv:2412.05617v1. Disponível "
    "em: <https://arxiv.org/abs/2412.05617>. Acesso em: 21 set. 2026.",

    "Richardson, C. 2019. Microservices Patterns: With Examples in Java. 1ed. Manning "
    "Publications, Shelter Island, NY, EUA.",
]
for r in refs:
    par(r, align=WD_ALIGN_PARAGRAPH.LEFT, recuo=0, ls=1.0, depois=0)
    par(ls=1.0)

d.save(SAIDA)
print("gerado:", SAIDA)
print("paragrafos:", len(d.paragraphs), "| tabelas:", len(d.tables))
