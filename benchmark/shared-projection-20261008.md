# Compartilhamento das projeções — etapa 1

A projeção dos estados agora preserva os ramos da árvore persistente. Na FIXTURE02,
a memória viva total caiu 48,28% e os nós vivos dos mapas caíram 95,65% na comparação
limitada a 2.048 contextos. As entradas dos resumos e os contadores do solver são
iguais nas duas versões. A mudança reduz armazenamento e alocações; não resolve
a multiplicação combinatória de contextos nem qualifica a execução completa da
fixture original.

## Implementação

`DependencyEnvironment.Projection` seleciona os campos relevantes diretamente na
árvore imutável. Um ramo inteiramente preservado mantém sua identidade; excluir
campos combina os ramos restantes usando a mesma ordem da árvore. Cada seleção
memoriza a projeção de subárvores por identidade. Chaves e valores desse cache são
fracos: o cache não mantém vivos os estados descartados, inclusive quando o
resultado é o próprio ramo de entrada. Perder um item do cache apenas repete a
mesma projeção.

`DependencyFlow.subscribe` reutiliza uma seleção por ponto de entrada/endpoint.
O índice de relevância tem sucessores fechados para os pontos já descobertos;
portanto, estender o índice não muda a seleção de um ponto existente. A projeção
não passa por HashMap nem reinserção completa na árvore. A igualdade e o hash dos
mapas continuam baseados no conteúdo; identidade é usada somente para reconhecer
ramos compartilhados no cache. Campos explicitamente UNKNOWN permanecem presentes,
e campos ausentes permanecem ausentes. Handlers, fatos, chaves dos resumos e regras
de junção mantêm sua semântica.

Existe um único caminho de projeção, usado em todos os programas. Não foi mantido
o algoritmo anterior como fallback, nem criado seletor por tamanho ou por fixture.
Não há interning global que retenha todas as árvores da análise.

## Experimento controlado

Baseline: commit `e9458d53f3a0658c00495403b18b189d0b8e3e4d`, JAR
`b3f266e5a553a120488841d1428f2947434bb52487bb03661728b504885785d4`.
JAR medido da etapa 1:
`4e5ba786e1d7dbd666233f728f8c71600b8e665ff4584b6733a80e0a055b13af`.

Java Temurin 21.0.12+1.1, heap 512 MiB nas duas versões, uma JVM por vez. Foram
três repetições por versão, intercaladas, com parada diagnóstica em 2.048 contextos.
O supervisor interromperia uma execução acima de 850 MiB de RSS ou 60 segundos;
nenhum desses limites foi acionado. Não houve aumento de heap.

A fonte é a mesma reconstrução, com SHA-256
`71408d8c98ae04600f119ad98fd3e16c3066c20babdec8c4ab6ada8632a9c9d3`.
A igualdade integral com o vídeo continua não verificada, conforme o cabeçalho da
fixture. Nenhuma fonte ou oráculo foi alterado.

| Medida, mediana de três execuções | Antes | Depois | Redução |
| --- | ---: | ---: | ---: |
| Nós vivos de DependencyEnvironment | 1.638.993 | 71.340 | 95,65% |
| Bytes vivos desses nós | 65.559.720 | 2.853.600 | 95,65% |
| Bytes vivos totais inventariados | 117.590.480 | 60.812.776 | 48,28% |
| Bytes cumulativos alocados pela thread main até a parada | 1.848.314.496 | 958.768.576 | 48,13% |
| Tempo do solver até a parada | 1,595 s | 1,119 s | 29,82% |
| RSS máximo, incluindo instrumentação e diagnóstico | 405.344 KiB | 317.160 KiB | 21,76% |

Os histogramas foram obtidos dentro do próprio processo, via DiagnosticCommand,
com coleta completa de lixo. Incluem o custo vivo do novo cache: no primeiro
histograma do candidato havia 70.997 entradas WeakHashMap (2.839.880 bytes),
70.985 WeakReference (2.271.520 bytes) e 596.976 bytes em tabelas WeakHashMap.
Portanto, a economia total já incorpora esse overhead; não foi calculada somente
pela retirada dos nós antigos.

O contador ThreadMXBean mede os bytes cumulativos alocados pela thread main,
incluindo frontend e solver, antes do diagnóstico. Não representa memória viva.
O tempo do solver também é capturado antes do histograma e do cálculo dos hashes.
RSS e tempo externo incluem esses diagnósticos e JFR. As três observações são uma
comparação descritiva com JVMs novas, não uma garantia de ganho em todo programa.

Os seis JFRs foram processados em streaming com heap 128 MiB. Suas estimativas
amostradas de alocação corroboram a retirada da reconstrução das árvores, mas
não substituem os contadores diretos da tabela, sobretudo em execuções curtas.
Tentativas anteriores do supervisor/JFR falharam no diagnóstico; seus logs foram
preservados e não entram nas seis medições qualificadas.

## Equivalência no ponto de parada

Todas as seis execuções apresentam:

- 2.048 contextos, 16 pares de entrada/endpoint;
- 15.282 visitas, 13.542 estados BEFORE e 1.590 itens pendentes;
- 1.571.784 posições projetadas, das quais 1.557.347 são UNKNOWN puro;
- SHA-256 das chaves completas dos resumos:
  `f3379ae47baff6cc9dcf9c1bd9c2739f5aca849e176289689db4d70414a5d2f3`.

O hash incorpora entrada, endpoint, valores por declaração, candidatos, flag
unknown, handlers e fatos. Não elimina diferenças entre estados para melhorar os
números. As seis execuções encerram com código 2 e DIAGNOSTIC_STOP, sem publicação
de dependências. Isso não é PASS da fixture nem OOM. A equivalência de saída é
verificada separadamente em fixtures que terminam.

## Validação e limites

- Focados: 27 testes, zero falhas/erros/skips.
- FAST: 1.040 testes, zero falhas/erros/skips; a suíte da estrutura persistente agora faz parte do FAST.
- Discovery: 40/40 fontes com hash conferido, mesmos bytes após normalizar somente o caminho físico da fonte, mesmos diagnósticos e mesmos contextos/itens de trabalho.
- CardDemo: 73/73, 815 relações preservadas, zero perdidas/adicionadas; JSON byte a byte e diagnósticos iguais ao solver anterior. Permanecem 26 completos e 47 PARTIAL, com heap 512 MiB.
- Standalone: JAR executado com heap 256 MiB, diretório isolado e ambiente vazio.
- Gate documental e git diff --check: PASS.

Os resultados de validação e os hashes dos artefatos estão no
[registro estruturado](shared-projection-20261008.json). Foram executados testes de
conteúdo, hash, imutabilidade, compartilhamento, ausência versus UNKNOWN, remoções
aleatórias e recomputação após remoção de cache, além das suítes semânticas e dos
corpora descritos no registro. O JAR reconstruído para a qualificação tem as
mesmas 4.554 entradas de classes do JAR medido, byte a byte.

A etapa 2 permanece aberta: compartilhar a análise entre entradas distintas.
Esta mudança não une contextos, não descarta candidatos e não promete concluir a
FIXTURE02 com 512 MiB. Não foi repetida a execução irrestrita que derrubava a
sessão. A qualificação histórica com heap maior, a matriz de 30 casos e o full de
1.743 testes não são apresentados como executados nesta etapa.
