# Compartilhamento de trabalho — etapa 2

O solver agora reutiliza transformações locais entre estados de invocações
distintas. Na comparação limitada da FIXTURE02, 15.282 visitas exigiram somente
120 avaliações: as outras 15.162 reutilizaram resultados. O tempo do solver caiu
32,8% e os bytes alocados caíram 17,2%. A memória viva aumentou 5,7%, principalmente
pelos caches de projeção. Os contextos e suas entradas continuam distintos;
a quantidade combinatória de contextos ainda não foi resolvida.

## Como o trabalho é compartilhado

Considere dois chamadores que chegam ao mesmo `MOVE A TO B`. Ambos têm o mesmo
valor de A, mas valores diferentes em outros campos. A transferência passa a ser
calculada uma vez para a instrução e seus operandos. Ela devolve uma alteração
de B, que cada chamador aplica à sua própria árvore persistente. Os demais campos
continuam pertencendo ao chamador. Portanto, reutilizar essa transformação não
combina estados nem troca valores entre os chamadores.

`DependencyFlow.step` seleciona os operandos e consulta a chave
`Computation(node, input)`. O resultado é uma lista imutável de ações, com patches
das alterações nos valores, handlers e fatos. A avaliação inclui filtros de IF,
EVALUATE, GO TO e fases de PERFORM. O agendamento aplica os patches ao estado
completo e resolve as continuações no contexto do chamador. Essa separação mantém
fora do cache as decisões que dependem do endpoint e da invocação.

A seleção usa os efeitos da própria instrução e fecha os acessos indiretos sobre
aliases, grupos, campos irmãos e elementos de tabela. Uma escrita parcial pode
precisar dos irmãos para atualizar o valor lógico do grupo; selecionar somente
o destino perderia informação. Escritas escalares completas comprovadas dispensam
o valor anterior, exceto quando o destino também é fonte. Ausência e UNKNOWN
explícito permanecem diferentes. As consultas continuam usando o estado BEFORE
completo, preservando nomes dinâmicos e correlações do chamador.

`Selection` é a seleção comum dos operandos e das entradas dos resumos.
`DependencyEnvironment.Projection` descarta intervalos sem operandos antes de
percorrer ou memorizar seus ramos. A junção da árvore persistente pula ramos
idênticos e calcula a união dos ramos diferentes, inclusive entre mapas com
conjuntos de chaves diferentes. Os caches das projeções continuam fracos; o cache
das transformações retém suas chaves e patches durante a análise.

Existe um único caminho de avaliação. Não há solver anterior como fallback,
seletor por tamanho, limite de candidatos para obter sucesso ou tratamento da
fixture por nome. As bifurcações das ações correspondem às construções de controle
COBOL. A descoberta do controle e a semântica das transferências permanecem no
solver canônico.

## Medição controlada

Baseline da etapa 1: commit `f28fa857e20beb8ac6bad268fffb3154f684b674`, JAR
`d2ea08865ff54c182d8fe9331bf31fa5a2e987f5678c72b67c9deaebd9f56bd6`.
JAR candidato e qualificado:
`84ac7129a0c4129ead417c643d1a4b12488376bfdf666c26ddb0c77068bd43e9`.
As 4.562 entradas de classes do JAR medido e do qualificado são idênticas.

Java Temurin 21.0.12+1.1; heap 512 MiB nas duas versões; uma JVM por vez;
três repetições intercaladas por versão. Parada diagnóstica em 2.048 contextos,
com supervisor de RSS de 850 MiB e tempo de 60 segundos. Nenhum desses dois
limites foi acionado. Não houve aumento de heap.

Fonte original reconstruída, sem alterações, SHA-256
`71408d8c98ae04600f119ad98fd3e16c3066c20babdec8c4ab6ada8632a9c9d3`.
A igualdade integral com o vídeo continua não verificada, conforme seu cabeçalho.

| Medida, mediana de três execuções | Etapa 1 | Etapa 2 | Variação |
| --- | ---: | ---: | ---: |
| Avaliações locais | 15.282 | 120 | −99,21% |
| Avaliações reutilizadas | 0 | 15.162 | — |
| Tempo do solver até a parada | 0,949 s | 0,637 s | −32,82% |
| Bytes cumulativos alocados pela thread main até a parada | 933.490.120 | 772.822.840 | −17,21% |
| Bytes vivos totais inventariados | 60.756.024 | 64.232.672 | +5,72% |
| Nós vivos de DependencyEnvironment | 71.340 | 71.441 | +0,14% |
| RSS máximo, incluindo instrumentação e diagnóstico | 322.844 KiB | 313.292 KiB | −2,96% |

O contador direto ThreadMXBean inclui frontend e solver, antes dos diagnósticos;
ele mede alocação cumulativa, não memória viva. O tempo do solver é capturado antes
do histograma e dos hashes. Os histogramas vêm do DiagnosticCommand dentro do
processo, com coleta completa de lixo. RSS inclui JFR e os diagnósticos.

No segundo par de histogramas, as entradas WeakHashMap aumentaram de 70.997 para
111.913 (+1.636.640 bytes), e WeakReference aumentou de 70.985 para 111.901
(+1.309.312 bytes). As tabelas WeakHashMap acrescentaram 312.320 bytes e as
entradas TreeMap, usadas para selecionar intervalos, 495.320 bytes. A economia
de alocação não deve ser apresentada como economia de memória viva nesta etapa.

Os seis JFRs foram processados em streaming com heap 128 MiB. As amostras ajudam
a localizar alocações, mas suas estimativas não substituem os contadores diretos
em execuções curtas. Os números são descritivos de três JVMs novas; não garantem
o mesmo ganho em todos os programas. Uma instrução com muitos operandos diferentes
terá menos oportunidades de reutilização.

## Equivalência e validação

As seis execuções conservam 2.048 contextos, 16 pares de entrada/endpoint,
15.282 visitas, 13.542 estados BEFORE e 1.590 itens pendentes. As entradas têm
1.571.784 posições projetadas, das quais 1.557.347 são UNKNOWN puro. O hash das
chaves completas dos resumos é igual nas seis:
`f3379ae47baff6cc9dcf9c1bd9c2739f5aca849e176289689db4d70414a5d2f3`.
Ele inclui entrada, endpoint, valores, candidatos, unknown, handlers e fatos.

Todas encerram com código 2, DIAGNOSTIC_STOP e sem publicação de dependências.
Isso não é PASS da análise completa nem OOM. A equivalência de saída foi
verificada separadamente nas fontes que terminam:

- Focados: 188 testes, zero falhas/erros/skips, incluindo leis da junção e seleção esparsa.
- FAST: 1.044 testes, zero falhas/erros/skips, em 55,508 s.
- Depois do FAST, o teste de reutilização foi fortalecido para consultar também o destino escrito. Os 31 testes de mapas/resumos passaram novamente; produção inalterada.
- O teste de reutilização mantém 41 contextos distintos, 40 nomes específicos dos chamadores e o destino comum, com pelo menos 39 avaliações reaproveitadas. Há também caso negativo de correlação com escrita parcial.
- Discovery: 40/40, hashes das fontes conferidos; JSON byte a byte, diagnósticos, contextos e itens de trabalho iguais à etapa 1, usando o mesmo caminho físico das fontes.
- CardDemo: 73/73, 815 relações preservadas, zero perdidas/adicionadas; JSONs e diagnósticos iguais à etapa 1. Permanecem 26 completos e 47 PARTIAL. Heap 512 MiB; pico de RSS 484.456 KiB.
- Standalone: JAR em diretório e ambiente isolados, heap 256 MiB, PASS.

A primeira seleção de operandos perdeu informação em sete testes de grupos e
overlays. A seleção foi corrigida para incluir os acessos indiretos necessários;
os oráculos não foram alterados. Fonte e logs dessa tentativa permanecem na
evidência bruta. Os testes semânticos existentes incluem snapshots congelados de
fixtures geradas, handlers, tabelas, aliases, grupos e endpoints.

As métricas laterais agora incluem `evaluations` e `reusedEvaluations`.
`workItems` continua contando as visitas do solver; reutilização não reduz
automaticamente a quantidade de visitas. O JSON de dependências permanece igual.

O [registro estruturado](shared-work-20261008.json) contém as observações, hashes
dos fontes qualificados e manifesto da evidência bruta. O gate documental e
`git diff --check` também foram executados.

## Limite restante

Compartilhar o cálculo local reduz seu custo por visita. Cada entrada distinta
ainda conserva seu contexto, seus estados BEFORE e suas continuações. Assim, a
FIXTURE02 ainda pode multiplicar contextos: não foi qualificada até o fim com
512 MiB, nem repetida a execução irrestrita que derrubava a sessão. Compartilhar
um resumo de procedimento inteiro de forma paramétrica exigiria uma mudança
adicional; esta implementação não apresenta esse problema como resolvido.

O full histórico, a matriz ampliada de escala e o caso de 117 mil linhas não
foram reexecutados nesta etapa. Seus resultados anteriores não são medições
desta implementação.
