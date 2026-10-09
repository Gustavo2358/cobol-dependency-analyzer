# Discovery: limitar precisão sem cortar dependências

Há uma fronteira promissora: **eliminar o histórico de invocações da pré-análise
auxiliar de controle, preservando os endpoints, valores e retornos reais no
DependencyFlow**. O protótipo isolado passou 127 testes focados e os testemunhos
semânticos. Não foi encontrado um número universal seguro para cortar
profundidade ou fundir entradas. A aproximação de controle também não eliminou
a segunda parede: a relação contexto × posição em BEFORE.

Não houve alteração de produção, aumento de heap ou novo modo do produto.
[Contadores, manifests e hashes das evidências](precision-budget-discovery-20261009.json).
Os overlays e seus knobs existem somente em `benchmark/profiling` e em resultados
ignorados pelo Git. Não estão qualificados para implantação.

## Experimento e segurança

- Fonte congelado: `b6f0ac2c50a269c57897769a3f587b3384696bca`.
- JAR: `benchmark/results/state-flyweight-20261009/flyweight.jar`, SHA-256
  `e623c8788d47a89552601bfa5e23699419fda5bcace51b97a99a94b73eb00dac`.
- As 2.508 classes de target/classes correspondem byte a byte ao JAR.
- Java 21.0.12+1.1; JVMs de análise sequenciais, heap 512 MiB, metaspace
  128 MiB, memória direta 32 MiB, guarda RSS 768 MiB, reserva do host 2.048 MiB.
  Compiladores e leitores JFR usam no máximo 128 MiB. Timeout 45 s; duas sondas
  finais N=384 permitem 60 s, com as mesmas guardas de memória.
- `--max-work=1000000000` evita que um limite de trabalho baixo masque a parede;
  as guardas externas continuam ativas. O número não limita bytes ou tempo.
- Raw: `benchmark/results/precision-budget-20261009/`. Logs, manifests, entradas,
  telemetria, resultados RED e hashes estão preservados. O JSON deste relatório
  resume as medições e permite conferir os arquivos brutos.

## Cortar profundidade perde trabalho legítimo

Imagine MAIN fazendo PERFORM A, que faz PERFORM B, que chama DEEPCALL. Depois
que tudo retorna, MAIN chama AFTER. Se paramos de explorar no meio da cadeia,
não descobrimos DEEPCALL. Também não aprendemos que a cadeia retorna; por isso
podemos perder AFTER. Na cadeia de 32 níveis, cortes 4 e 16 perderam ambos.

O probe mede a menor profundidade de entrada dos resumos reutilizados: ReturnTo
acrescenta um nível, Forward mantém o nível. Não é uma enumeração de todos os
caminhos com uma pilha limitada. Mesmo essa forma de corte já tem perdas.

Nas caixinhas N=64/128, corte 1 mantém os CALLs literais e reduz visitas do
fluxo de 133.519 para 100.751 / 529.167 para 398.095. Isso não o torna correto:
a pré-análise de controle permanece quadrática e o exemplo da cadeia reprova.
Para qualquer K fixo é possível construir uma cadeia K+1 com uma dependência
relevante. O limite observado num corpus não é uma garantia para fontes futuros.

## Fundir entradas conserva possibilidades, mas muda a resposta

No testemunho caller-correlation, o primeiro chamador recebe ONLY e o consulta.
O segundo recebe UNOBS e termina sem consultá-lo. Fundir esses estados faz o
primeiro resultado passar a conter UNOBS: uma dependência impossível aparece.

Guardar 1, 4 ou 8 entradas precisas antes de fundir o excedente também reprova
caller-many-12/24: aparece o nome escrito pelo último chamador, nunca observado
na execução real. Com orçamento zero aparecem ainda INITIAL e um resto
DYNAMIC_REMAINDER. Esses casos não perderam nomes conhecidos, mas mudaram
precisão/diagnósticos e, portanto, não mantiveram o contrato observável completo.

Na fixture de caixinhas, orçamento zero nem reduz contextos ou visitas: a
multiplicidade vem das diferentes famílias de entrada/endpoints, e não de
valores diferentes dentro da mesma família. Essa intervenção ataca outro eixo.

## Limitar o controle auxiliar exige representar retornos opcionais

Primeiro agrupamos escopos excedentes numa fronteira curinga, mantendo os
endpoints reais do fluxo de valores. Um ponto do grafo pode parar para uma
invocação e continuar para outra. A fronteira curinga admite as duas opções.

Esse detalhe tem consequência para a relevância de dados. BODY pode escrever
NEW ou voltar sem escrever, conservando KEEP. Se o grafo auxiliar considera só
a continuação lexical que mais tarde escreve POISON, ele conclui erroneamente
que TARGET sempre será sobrescrito. A projeção elimina KEEP. Os primeiros
protótipos perderam KEEP e acrescentaram DYNAMIC_REMAINDER.

A correção experimental introduz MAY_STOP: a interseção de escritas garantidas
inclui a alternativa vazia de um retorno naquele ponto. Escritas locais completas
continuam substituindo valores; o resumo não pode usar uma escrita futura de uma
continuação opcional para eliminar uma entrada necessária.

Os nomes históricos `scopeN-safe` nos logs significam somente essa correção de
passthrough. Não são prova de segurança geral. Os 35 runs corrigidos nos sete
sentinelas compararam JSON e diagnósticos integralmente iguais.

Mesmo corrigida, a fusão sozinha não resolve o algoritmo: N=256 cai de 3.683.854
para 7.694 células de controle, mas os fatos de resultados de controle crescem
de 7.355.921 para 15.752.721. A fronteira curinga passa a oferecer muitas saídas
a cada chamada. O solver fecha essas combinações novamente. JFR N=256 atribui
1.893 de 2.117 amostras main (89,4%) ao controle; publish e sua lambda predominam.
O heap vivo N=128 pós-GC cai só de 153.450.112 para 140.244.216 bytes (8,6%).
Esses runs com censo/JFR não são medições pareadas de desempenho.

## O limite promissor: zero histórico apenas na pré-análise

O protótipo physical abandona o fechamento chamada × saída nessa fase. Constrói
um grafo físico conservador, com uma célula por posição, entradas dos corpos,
continuações físicas e possibilidade de retomada de uma chamada. Fronteiras
podem retornar sem atravessar suas continuações; MAY_STOP participa da relevância.
O Binding original fornece o endpoint da retomada. Não se produzem colunas de
resultados nem reinvocações de controle para cada escape descoberto.

```text
Pré-análise auxiliar                       Fluxo de valores

      PERFORM BODY                         chamador A -> BODY -> retorno A
       /         \                         chamador B -> BODY -> retorno B
  entra BODY    pode retomar                         |
       |         chamador                   GOBACK encerra; sem retomada
  pode parar OU continuar

Uma posição física                         Endpoints e entradas reais mantidos
Possibilidades conservadoras               Resultados substituídos no chamador
```

A pré-análise serve para demanda de entrada, observação, SCCs e ordem de visita.
Ela não precisa produzir valores de dependência. Admitir uma retomada nesse
mapa não executa o CALL depois de um GOBACK: DependencyFlow ainda exige um
resultado real compatível com a invocação. O limite se aplica à distinção
auxiliar entre históricos, não à profundidade executada ou à pilha COBOL.

Isso é uma proposta de mudança geral do solver canônico de controle. Uma futura
implementação deve substituir o fechamento antigo, sem flag de produção ou
caminho especial para caixinhas. A prova de que o grafo é uma sobreaproximação
precisa cobrir fronteiras, escapes ancestrais, handlers e declarativas. Além
disso, SCCs mais amplas alteram especialização e particionamento de resumos;
a equivalência observável geral não decorre automaticamente de reachability
conservadora. Os testes abaixo são evidência direta, não uma prova universal.

## Resultados do protótipo physical

Três pares N=128, ordem alternada, sem JFR/censo, mesmos fontes e guardas:

| Mediana | Produção congelada | Physical |
| --- | ---: | ---: |
| Tempo | 6,719 s | 3,843 s |
| Pico RSS | 422,8 MiB | 350,3 MiB |
| Células de controle | 924.430 | 3.854 |
| Entradas BEFORE | 397.324 | 397.324 |
| Visitas do fluxo | 529.167 | 529.167 |

Tempo mediano cai 42,8%; pico RSS mediano cai 17,2%. JSON completo e diagnósticos
são iguais nos seis runs. A economia não depende de descartar estados de valores.

Em N=256, produção concluiu em 43,234 s numa rodada anterior desta mesma campanha.
Physical concluiu em 13,341 s, com 7.694 células, 1.027 contextos e 1.581.068
entradas BEFORE. Mesma entrada por SHA-256, mesmo JSON completo e diagnósticos
comparados diretamente. São duas observações de rodadas diferentes, não mediana
nem par controlado. A tentativa de baseline na rodada physical-scale excedeu
45 s; seus campos additional/missing contra saída inexistente não têm significado
semântico. O runner final registra comparações incompletas como UNCOMPARABLE.

O seletor fixo N=128 cai de 4,042 para 1,465 s; CALL dinâmico N=16 de 1,467 para
1,314 s, conservando os nomes, proveniência e PARTIAL/DYNAMIC_REMAINDER. Há só
um par em cada caso. A fixture02 reconstruída mantém o resultado vazio e zero
diagnósticos, mas não acelera: 1,769 contra 1,971 s, contextos 25 contra 40.
Ela não representa o comportamento das caixinhas return-fanout.

Os dez testemunhos do piloto physical mantêm JSON e diagnósticos, incluindo
retorno sem escrita, escrita desconhecida completa, GOBACK, EXIT SECTION,
escape externo, muitos chamadores e cadeia profunda. Cinco classes JUnit já
existentes foram executadas com classes isoladas sobre o classpath congelado:
DependencySummaryTest, DependencyAnalyzerTest, PerformControlCompletionTest,
ConditionalDependencyFactsTest e FactDependencyLocalityTest. **127/127 passam
na baseline e no protótipo**, incluindo 24 digests de dependências, proveniência
e diagnósticos congelados e negativos de correlação de campos/índices.

## A parede que permanece em N=384

A produção congelada teve OOM de heap aos 19,8 s. Physical alcançou o limite
externo de 45 s; a sonda com 60 s permitido teve OOM aos 45,1 s. Sua fila de
valores já havia esvaziado: há 3.551.244 entradas BEFORE e 1.539 contextos,
2.304 planos de continuação e 4.618 cálculos locais. O OOM ocorre depois desse
marcador e antes de publicar o JSON. A gravação JFR dessa sonda ficou vazia pela
saída forçada; não foi usada como perfil válido.

Uma segunda sonda, com gravação finalizada aos 25 s e log GC, foi interrompida
pela guarda RSS aos 33,3 s. Ela também chegou ao fim da fila do fluxo. No fim,
Full GC repetidos deixam 506–509 MiB vivos de um heap de 512 MiB. O trecho JFR
válido anterior ao limite tem 532 amostras main: 263 em enqueue/join, 149 em
outro dataflow e apenas sete em controle. Location.equals é o primeiro método
de produto em 204 amostras. O perfil mudou de parede: controle deixou de dominar.
A gravação cobre somente os primeiros 25 s; não localiza a alocação exata que
falhou depois. Não houve stack de OOM válido: a guarda interrompeu a tentativa.

O tamanho BEFORE é aproximadamente quadrático nesta família: N=128/256/384
retém 397.324 / 1.581.068 / 3.551.244 entradas. Limitar a pré-análise não limita
essa relação nem as observações posteriores. **N=384 não está qualificado como
convergência completa e o protótipo não elimina OOM.** Manter a guarda é necessário.

## Decisão e fundamento algorítmico

Recomenda-se desenvolver a pré-análise física conservadora como substituição
canônica, com validação das fronteiras indicadas. Para a parede restante,
priorizar a fatoração exata `(posição,estado) -> conjunto de contextos` e propagação
de deltas de membros, mantendo ausências e vínculos de retorno. O discovery de
[relações/HAMT](hamt-relation-discovery-20261009.md) reconstruiu os mapas capturados
sem perdas e mediu repetição suficiente para isso. A reconstrução não qualifica
um novo algoritmo de convergência, mas dá uma base melhor que escolher uma
profundidade arbitrária. Esse segundo solver não foi implementado nesta investigação.

A distinção entre resumir e truncar vem da interpretação abstrata: o trecho não
explorado deve ser representado por uma aproximação conservadora, não eliminado.
[Cousot, introdução à interpretação abstrata](https://www.di.ens.fr/~cousot/AI/IntroAbsInt.html)
explica a inclusão de comportamentos possíveis e falsos positivos. A técnica de
[Rival e Mauborgne, Trace Partitioning, ESOP 2005](https://www.di.ens.fr/~mauborgn/publi/esop05.pdf)
permite juntar partições e resumir iterações sem descartá-las. Essas garantias de
soundness não prometem manter precisão, nomes concretos conhecidos e diagnósticos
idênticos. Nossas sentinelas fazem essa distinção observável.

## Qualificação e reprodução

A fronteira mudou só em ferramentas de benchmark, oráculos novos e evidências.
Produção/CLI/wire permanecem intactos. FAST 1.093 testes e CardDemo 73 fontes da
campanha flyweight continuam evidência **reutilizada do produto congelado**, não
do overlay. CardDemo não foi executado de novo nesta investigação. Não se afirma
paridade geral ou qualificação de produção; uma implementação canônica deverá
executar os gates obrigatórios do repositório depois dos testes de fronteira.

```sh
# JAVA_HOME deve apontar para o JDK 21 já disponível; nenhum download necessário.
python3 benchmark/profiling/precision-budget-probe.py /tmp/precision-overlay \
  --jar benchmark/results/state-flyweight-20261009/flyweight.jar \
  --ref b6f0ac2c50a269c57897769a3f587b3384696bca
python3 benchmark/run-precision-budgets.py /tmp/precision-runs \
  --jar benchmark/results/state-flyweight-20261009/flyweight.jar \
  --overlay /tmp/precision-overlay \
  --cases conditional-passthrough caller-correlation depth-32 fanout-128 \
  --variants disabled depth4 context0 physical
```

Diferenças nos cortes/fusões são resultados esperados RED, não regressões
ocultadas. Os scripts preservam cada resultado, exigem a validade do oráculo da
baseline e nunca qualificam o overlay para produção. A primeira rodada dynamic-16
foi interrompida por um oráculo incorreto do runner (assumia sem unknown); o raw
foi preservado e uma nova rodada corrigiu essa expectativa. Também foi preservada
a perda KEEP da aproximação ingênua; não se reescreveu evidência para obter PASS.
As duas fixtures de profundidade tiveram uma linha final de espaços removida
para o check de whitespace do Git. Uma nova rodada normalizada reproduziu os
resultados: baseline/disabled/physical iguais e corte 4 perdendo AFTER/DEEPCALL.
Os fontes e hashes anteriores continuam nos dados brutos.
