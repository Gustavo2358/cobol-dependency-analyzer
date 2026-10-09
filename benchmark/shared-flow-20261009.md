# Compartilhamento do fluxo entre entradas

O solver agora percorre o corpo de um procedimento com parâmetros de dados,
conservando o vínculo com cada chamador. No experimento de 400 entradas
distintas, os contextos caíram de 401 para 2 e as visitas de 28.002 para 1.668.
As 400 dependências e suas localizações permaneceram byte a byte idênticas.
O tempo interno da análise, incluindo os novos custos, caiu 46,3%.

## Funcionamento operacional

Considere dois PERFORMs que chegam a `MOVE A TO B`: o primeiro traz
`A='PROGA'`, o segundo traz `A='PROGB'`. A versão anterior criava dois
contextos e percorria o corpo em cada um. O cache local só ajudava quando os
operandos concretos de uma instrução coincidiam.

Agora, o corpo começa com um parâmetro A. A atribuição produz uma expressão
para B em função desse parâmetro. O segundo chamador assina o mesmo resumo,
em vez de enfileirar novamente as instruções do corpo. Quando o resumo retorna,
substituímos seus parâmetros pela entrada inteira do chamador: B recebe PROGA
no primeiro retorno e PROGB no segundo. Os campos preservados continuam vindo
da entrada de cada chamador. Uma escrita explícita de UNKNOWN continua
substituindo o valor anterior.

Uma consulta dentro do corpo guarda uma expressão sobre o estado BEFORE.
Ao terminar o fluxo, o motor resolve essa expressão pelos vínculos com os
chamadores e une os resultados. Substituir a entrada inteira é essencial:
uma escrita parcial com os pares `(AAAA0000,1111)` e `(BBBB0000,2222)` produz
AAAA1111 e BBBB2222, sem inventar AAAA2222 ou BBBB1111. Chamadas aninhadas e
entradas independentes de handlers conservam o contexto proprietário desse
vínculo. A resolução usa um grafo e worklist; cadeias longas usam uma pilha
explícita, sem consumir a pilha Java.

`DependencyEnvironment<V>` é a árvore persistente comum a valores e expressões.
`Calculation` guarda a instrução, seus operandos paramétricos e o destino.
Esses nós são internados por chave e comparados por identidade, evitando
reconstruir ou fazer hash recursivo de uma cadeia longa. Ao receber valores
concretos, a expressão usa a mesma função `transfer` que já define MOVE,
grupos, tabelas, aliases e escritas opacas. Não há uma segunda implementação
dessas operações. O resultado concreto também é memoizado.

Resultados são entregues por assinante. Adicionar um novo chamador entrega a
ele os resultados disponíveis; uma mudança posterior notifica os assinantes
existentes. Não se reenvia o resultado a todos os antigos apenas porque um
chamador novo chegou. O teste com 100 entradas e duas condições distintas
exige três contextos e menos de 110 entregas, além dos nomes exatos.

## Controle compartilhado e especialização necessária

`DependencyControl` fecha equações de controle por `(posição, endpoint)`,
independentemente dos valores concretos. A continuação de um PERFORM só entra
no grafo quando seu corpo produz uma saída compatível. Equações recursivas
começam sem saídas: um ciclo sem retorno não pode criar o retorno que falta.
Entradas independentes podem produzir consultas, mas não devolvem suas saídas
ao trecho que as disparou. O índice fornece os sucessores à relevância e
identifica as consultas estruturalmente alcançáveis.

A regra de entrega de conclusão, escape ou término é comum ao índice e ao
fluxo de valores. Removemos a enumeração antiga dos sucessores em
`inputEffect` e a resolução duplicada dos escapes em `deliver`.
O índice aproxima os branches possíveis; o fluxo de valores continua
aplicando os predicados. São duas relações necessárias no caminho canônico,
sem seleção por tamanho, nome de fixture ou solver de fallback.

O domínio paramétrico conserva concretas as entradas que alimentam predicados
observáveis e escritas em recorrências. Isso é fechado sobre suas fontes,
aliases, grupos e elementos de tabela. Predicados precisam distinguir os
branches viáveis. Recorrências precisam convergir no domínio de valores:
representar cada iteração como mais uma expressão produziria uma cadeia
infinita. A identificação dos ciclos usa Kosaraju iterativo; efeitos de corpos
invocados por ciclos também entram nesse fechamento.

Essa especialização é uma regra do mesmo domínio, sem rota alternativa para
executar o programa. O cache de transformações locais permanece útil entre
assinaturas de controle distintas. A implementação compartilha o fluxo entre
entradas de dados diferentes; não promete compartilhar assinaturas arbitrárias
de controle nem eliminar todo crescimento combinatório.

## Medição

Baseline: commit `dde588a2909be1b36356eec94c0925b29996ead2`, JAR
`84ac7129a0c4129ead417c643d1a4b12488376bfdf666c26ddb0c77068bd43e9`.
Candidato medido e qualificado: JAR
`afae3a62c961dd5ae03a0cc29cbbfe9fca6d1aa54e0952d017431e0608ad200f`.

Java Temurin 21.0.12+1.1, JVMs novas e sequenciais, três repetições intercaladas
por versão, heap 512 MiB, metaspace 256 MiB, memória direta 64 MiB.
O supervisor interrompe em RSS 850 MiB ou 60 s. Nenhuma execução atingiu essas
guardas ou sofreu OOM. JFR `profile` fica limitado a 16 MiB por execução;
sua leitura é sequencial e em streaming com heap 128 MiB.

O caso de 400 entradas realiza 400 PERFORMs com nomes distintos. Cada corpo
tem 64 MOVEs de A para B e uma consulta; há outra consulta após o retorno.
Há 800 sites dinâmicos alcançáveis, deduplicados em 400 dependências.
O orçamento é 100 milhões em ambas as versões. Valores abaixo são medianas
de três execuções completas:

| Medida — 400 entradas | Baseline | Candidato |
|---|---:|---:|
| Contextos | 401 | 2 |
| Visitas ao fluxo de valores | 28.002 | 1.668 |
| Transformações locais calculadas | 27.202 | 1.267 |
| Aplicações concretas das expressões | não se aplica | 400 |
| Resolução de consultas paramétricas | não se aplica | 699 itens |
| Trabalho do índice de controle | não se aplica | 4.141 itens |
| Expressões de cálculo paramétrico | não se aplica | 64 |
| Entregas de resultados | não instrumentado | 401 |
| Tempo interno da análise | 386,46 ms | 207,47 ms |
| Tempo externo, incluindo startup/JFR | 1,663 s | 1,563 s |
| Pico de RSS | 223.536 KiB | 208.696 KiB |

O tempo interno caiu 46,3%; o externo, 6,0%; a mediana de RSS, 6,6%.
`dataflowNanos` engloba o índice de controle, a construção/propagação dos
resumos e a resolução paramétrica. A queda das visitas não esconde o custo
de instanciar os dados de cada chamador. Três repetições curtas não constituem
um intervalo de confiança estatístico. Amostras JFR são evidência auxiliar,
não contadores exatos de alocação ou CPU.
O peso mediano das amostras de alocação da thread principal caiu de
324.461.840 para 240.794.432 bytes (25,8%). Inclui frontend e startup;
não representa memória viva. As execuções geraram apenas 9–20 amostras de
CPU da thread principal, insuficientes para quantificar hotspots com precisão.

### Fixture original

A fixture reconstruída foi mantida intacta, SHA-256
`71408d8c98ae04600f119ad98fd3e16c3066c20babdec8c4ab6ada8632a9c9d3`.
Usamos orçamento de **20.000** em ambas as versões, suficiente para a nova
análise terminar e para limitar com segurança a versão anterior. Na baseline,
as três execuções pararam explicitamente com RESOURCE_LIMIT/código 2 e
preservaram a saída sentinela anterior. Não repetimos o antigo experimento
irrestrito de OOM; seu histórico permanece no relatório de profiling.

O candidato terminou três vezes com código 0, **25 contextos, 200 visitas,
475 pontos de controle e zero declarações rastreadas**. Medianas: 1,864 s
externos, 85,84 ms internos e RSS 298.552 KiB (291,6 MiB); o maior RSS foi
300.372 KiB (293,3 MiB).

A saída vazia é esperada: todos os parágrafos fazem PERFORM antes de CALL,
e a cadeia de PERFORMs alcançada pela entrada entra em ciclo sem retorno.
Nenhum CALL é alcançável; FINAL-PARA também não fornece uma saída a esse ciclo.
A conferência independente seguiu o primeiro PERFORM incondicional de cada
parágrafo: após 25 parágrafos distintos, P00140 volta a P00063. O traço completo
está no registro estruturado e em `original-control-oracle.json` na evidência bruta.
A análise termina porque o ponto fixo reconhece a ausência de retornos,
não porque o programa COBOL terminaria. O corte das leituras sem observações
alcançáveis elimina as combinações de valores que não poderiam produzir uma
dependência. O teste de 400 entradas, que realmente retorna, mede separadamente
o benefício dos resumos paramétricos.

## Evidência e limites

- Focados: 198 testes aprovados após a correção das entregas; a última regressão
  adicional de observação recursiva passou no FAST final.
- FAST final: 1.055 testes, zero falhas/erros/skips; 61,020 s no wrapper.
  Inclui leis do controle, 100 entradas com duas condições, correlação em
  escrita parcial, chamadas aninhadas, cadeias de 1.200 MOVEs e ciclos sem retorno.
- Discovery: 40/40, hashes das fontes conferidos; JSONs, diagnósticos e códigos
  preservados contra a versão anterior, usando os mesmos caminhos físicos.
  Nove casos mudaram suas contagens de trabalho/contextos, como esperado.
- CardDemo: 73/73, 815 relações, zero perdidas/adicionadas; JSONs e diagnósticos
  byte a byte iguais à versão anterior. Permanecem 26 completos e 47 PARTIAL.
  Heap 512 MiB, maior RSS 496.224 KiB; tempo externo somado 112,757 s.
- Standalone: JAR em diretório e ambiente isolados, heap 256 MiB, PASS.

As tentativas intermediárias e suas falhas estão preservadas na evidência bruta.
Corrigimos a retomada de EXIT PARAGRAPH em entrada independente, a retenção
excessiva de corpos de entradas independentes e o contexto proprietário de
handlers. Os oráculos de dependências não foram relaxados. Uma expectativa
de quantidade de contextos foi atualizada de 13 para 2 porque leituras e
escritas parciais agora compartilham o corpo; os nomes esperados ficaram iguais.

Combinações distintas dos valores especializados, handlers e fatos ainda podem
multiplicar contextos. Resultados com muitos candidatos e expressões que
precisam ler dados diferentes também têm custo próprio. O orçamento continua
falhando explicitamente, sem cortar candidatos nem substituir a falha por uma
aproximação silenciosa. O full histórico, a matriz ampliada de 30 escalas e o
caso de 117 mil linhas não foram reexecutados nesta etapa; não há garantia
universal de tempo ou memória para esses programas.

O [registro estruturado](shared-flow-20261009.json) contém comandos, medições,
hashes, validações e manifesto da evidência bruta. Os arquivos brutos ficam em
`benchmark/results/shared-control-20261009/`, ignorado pelo Git.
