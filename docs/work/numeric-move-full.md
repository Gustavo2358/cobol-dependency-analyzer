# Prioridade 2 — MOVE, tipos e valores numéricos

Status: IN_PROGRESS. Este trabalho amplia o recorte CP2.1; a prioridade inteira
continua aberta. O relatório `numeric-move-qualification.md` é a evidência daquele
recorte anterior, não uma afirmação de completude desta prioridade.

## Contrato corrente em desenvolvimento

SP 2.66 substitui `scalarInteger` por `scalarNumber`, com precisão, escala,
sinal e representação explícitos. `numericTransfers` substitui a prova de MOVE
inteiro. A mesma análise de tipos serve aos controles inteiros, que exigem escala
zero, e aos MOVEs numéricos. Não há dois algoritmos de conversão por versão.

A gramática publica o descritor numérico da PICTURE e a categoria de USAGE.
A análise usa esses fatos tipados; os projectors e o lower não reanalisam COBOL.
DISPLAY, COMP/BINARY, COMP-3/PACKED-DECIMAL e COMP-5 têm descritores distintos.
O ajuste de texto usa `RIGHT_FIT_SPACE` para preenchimento e truncamento, inclusive
origem DATA e referências qualificadas. SPACES reutiliza a semântica de figurativos.

## Regras e fontes

- [IBM: elementary move rules](https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=moves-elementary-move-rules):
  alinhamento decimal, truncamento, sinal do receptor e conversão entre categorias.
- [IBM: TRUNC](https://www.ibm.com/docs/en/cobol-zos/6.4?topic=options-trunc):
  BINARY depende da opção; COMP-5 usa a capacidade binária independentemente dela.
- [IBM: Language Reference 6.4](https://publibfp.dhe.ibm.com/epubs/pdf/igy6lr40.pdf):
  PICTURE com S/9/V/P, capacidade dos usos computacionais e regras de MOVE.

Para precisão `d` e escala `s`, o coeficiente recebido é truncado em direção a zero
após multiplicação por 10^s. Receptor sem sinal usa o valor absoluto. DISPLAY e
PACKED conservam os `d` dígitos menos significativos. COMP-5 usa módulo 2^w, onde
w é 16, 32 ou 64, e interpreta o resultado com o sinal declarado.

Enquanto TRUNC não é informado, BINARY só recebe prova positiva quando STD/OPT/BIN
concordam. A ausência da opção não é interpretada como uma escolha do usuário.
Provas de tipo não dispensam provas de acesso e armazenamento. Uma sobreposição
não pode ganhar duas células independentes, uma textual e outra numérica.

## Algoritmo e validação

O descritor de PICTURE é construído em uma passagem, sem expandir repetições.
USAGE herdado é memoizado por declaração. Os receptores são visitados na ordem do
MOVE; um receptor desconhecido limita as provas subsequentes de uma origem DATA.
A tradução cria uma quantidade constante de expressões por transferência.
Expoentes grandes usam resto modular e comparação de precisão; o algoritmo não
constrói os zeros implícitos de um literal com escala extrema.

A AIR em desenvolvimento transporta DECIMAL, `fit_decimal`, `wrap_integer`,
`to_int`, `to_decimal`, `abs` e multiplicação. O CFG percorre operandos pelo
contrato canônico `Operands.children`, preservando leituras dentro das conversões.
Oráculos escritos à mão executam a AIR e verificam sinal, escala, truncamento e
capacidade binária. Mutações de certificados e descritores devem ser rejeitadas.

A primeira medição desta ampliação processou 73 fontes / 292 etapas sem falha.
Resolveu 688 MOVEs adicionais: 4.928 → 4.240 ainda com gap. A comparação semântica
preservou 150 sites / 209 candidatos de programas, 391 / 378 de arquivos e
259 / 95 de dependências qualificadas. Os estados permaneceram 65 PARTIAL e
8 COMPLETE. Os 3.807 usos de condições 88 foram preservados.

Essa medição é histórica e antecede as ampliações documentadas abaixo.
O fechamento corrente segue o escopo causal finito da última seção, com inventário
por ocorrência e regressão final; não exige implementar todas as conversões COBOL.

## Figurativo ZERO

[IBM, figurative constants](https://www.ibm.com/docs/en/SS6SG3_6.3.0/pdf/lrmvs.pdf)
define ZERO/ZEROS/ZEROES pelo contexto: zero numérico ou repetição do caractere
zero até preencher o receptor textual. O contrato preserva a categoria
FIGURATIVE_ZERO e o ajuste ZERO_FILL, sem confundir o figurativo com o literal
numérico 0 ou o texto '0'. O algoritmo produz no máximo o tamanho do receptor,
com uma prova por receptor; não expande combinações de receptores.
O oracle exige '0000' para ZERO e '0   ' para '0' em PIC X(4), e zero para
PIC S9(4) COMP-3. Provas com regra, valor ou categoria divergentes são inválidas.

## Literal alfanumérico para número

As regras IBM de MOVE elementar tratam o emissor alfanumérico como inteiro sem
sinal. Para literal recebido por campo numérico, todos os caracteres precisam
ser dígitos. A análise verifica a sequência inteira em uma passagem e usa o
mesmo ajuste numérico; espaços, sinal, ponto e caracteres não numéricos não
recebem prova. O oracle exige '00052' → 52.00 em S9(3)V99 e '052' → 2 em PIC 9.

## Inteiro para texto

O MOVE de um inteiro DISPLAY ou PACKED para alfanumérico usa a magnitude sem
sinal operacional, os dígitos da PICTURE e as posições P, seguido pelo ajuste
do receptor. [IBM 6.3, regras elementares](https://www.ibm.com/docs/en/cobol-zos/6.3.0?topic=moves-elementary-move-rules)
e [migração de posições P](https://www.ibm.com/docs/he/cobol-zos/6.3.0?topic=programs-language-elements-that-changed-from-osvs-cobol).
A prova FORMATTED_INTEGER exige células locais tipadas e escala não positiva.
O lower produz integer_digits e fit_text; ambos conservam a leitura da origem.
A saída é limitada pela extensão do receptor; não há enumeração de valores.
Oracle: S9(5) com -23 para X(8) → '00023   '; 99PP com 2300 → '2300    '.
A extensão ainda precisa tratar as regras próprias de BINARY/COMP-5.

## INITIAL e tipo local

INITIAL determina a reinicialização da WORKING-STORAGE em cada chamada, não
altera PICTURE, USAGE ou a regra de MOVE. O tipo numérico e a transferência local
não dependem de conhecer o valor inicial. As obrigações de inicialização
continuam no produto de entrada; a prova de tipo não produz uma constante
inicial. Oracle: programa INITIAL com S9(4) COMP VALUE 0 e MOVE 12 publica o
tipo BINARY e o resultado 12, sem deduzir memória inicial da atribuição.
Fonte: IBM Language Reference 6.4, PROGRAM-ID/INITIAL e VALUE.

A regra de TRUNC documenta que BIN não afeta o emissor BINARY quando o receptor
não é numérico; a conversão textual usa a largura decimal da PICTURE. COMP-5
usará a capacidade nativa: 5/10/19 dígitos para signed 16/32/64 e 5/10/20 para
unsigned, acrescidos das posições P finais. Fontes: IBM TRUNC e USAGE COMP-5
na Language Reference 6.4. Oracle: 12 em S9(4) COMP → '0012    ';
12 em S9(4) COMP-5 → '00012   '; -7621 em S99 COMP-5 → '07621   '.

## TRUNC explícito

A opção CBL TRUNC é publicada no descritor numérico (`trunc`). STD usa a
PICTURE do receptor; BIN usa a palavra de 16/32/64 bits. OPT só recebe prova
quando o valor cabe na PICTURE, porque o resultado fora dela é imprevisível.
UNSPECIFIED conserva apenas a interseção de STD/OPT/BIN. TRUNC só se aplica a
BINARY; as demais representações exigem UNSPECIFIED no descritor.
O oracle independente usa 123451 para S99 COMP: STD = 51, BIN = -7621;
OPT e ausência da opção mantêm resultado aberto quando não há valor comum, com a conversão reconhecida. A mesma regra vale para emissor DATA.
Fonte: https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=options-trunc

## Acessos e preenchimento

INITIAL também conserva a prova de layout textual; apenas o estado de entrada
trata reinicialização. ZERO em região IBM1047 preenche cada byte com F0;
'0' continua preenchendo a sobra com espaços 40.
Modificação de referência sem comprimento usa extensão − posição + 1, com
limites positivos verificados. Posições dinâmicas continuam exigindo prova.
Fonte: https://www.ibm.com/docs/en/cobol-zos/6.3.0?topic=reference-modification

## Células locais em regiões com sobreposições

Uma região mista não exige que todo campo tenha representação conhecida para
admitir um MOVE local. Uma célula pode ser fechada quando todos os acessos
nominais elementares que a sobrepõem cobrem exatamente o mesmo intervalo e têm
o mesmo descritor de valor. Os aliases usam uma única identidade de célula.
FILLER não cria um acesso nominal, mas todo grupo que cobre seus bytes precisa
incluir as células cobertas no seu limite de leitura/escrita.

A geometria deriva de PICTURE/USAGE e das relações provadas; não infere CCSID
nem publica um codec. Intervalos de texto DISPLAY usam uma posição por byte;
COMP usa 2/4/8 bytes; COMP-3 usa ceil((dígitos+1)/2). REDEFINES usa o máximo
do componente. Cláusulas, dimensões ou relações sem prova bloqueiam a família.
Uma varredura dos intervalos ordenados fecha aliases iguais em O(n log n).
Consultas dos limites de grupos custam O(log n + k), com k células publicadas;
nenhuma combinação de visões ou valor de runtime é enumerada.

Oracle: a visão de saída BMS tem FILLER sobre o comprimento COMP da entrada.
Escrever esse grupo deve invalidar também a célula do comprimento. Campos
NAME-I/NAME-O com mesmo intervalo e PIC X compartilham a célula. Sobreposição
parcial e PIC 9 versus PIC X não criam células independentes.

## Literal inteiro para texto

A AST conserva os dígitos escritos do literal inteiro separadamente do seu
valor numérico. O emissor textual usa a magnitude sem sinal; a largura inclui
os zeros escritos. `-00052` para X(6) resulta em '00052 ', enquanto o mesmo
emissor para S9(5) continua sendo -52. O literal 0 resulta em '0     '; o
figurativo ZERO continua preenchendo todo o receptor. O contrato verifica
a correspondência entre dígitos e magnitude numérica. Nenhum consumidor
reanálisa o fonte.
Fonte: IBM elementary MOVE rules e a regra de tamanho de literal numérico
em https://www.govinfo.gov/content/pkg/GOVPUB-C13-d0cd47d3539e1d225361316057506135/pdf/GOVPUB-C13-d0cd47d3539e1d225361316057506135.pdf

## Provenance de COPY e prova de valor

No SP 2.66, `exact=false` indica mapeamento físico aproximado, inclusive COPY
REPLACING. Não invalida uma prova tipada de acesso whole-item e fitting com
binding único e certificado completo. O lower continua verificando tipo,
extensão e resultado; contratos históricos conservam a obrigação anterior.
Oracle: o mesmo MOVE literal para PIC X, com provenance aproximada, produz
a mesma AIR; remover o acesso ou forjar a extensão continua sendo rejeitado.

## Edição numérica em desenvolvimento

A gramática publica segmentos tipados e comprimidos de edição, com precisão,
escala e extensão. O lower não interpreta PICTURE. A opção DECIMAL-POINT IS
COMMA é lida do contexto gramatical do programa; moeda redefinida impede usar
implicitamente o símbolo padrão. O algoritmo visita a grafia e os segmentos
uma vez, sem expandir repetições. A AIR format_decimal recebe DECIMAL e produz
TEXT com segmentos explícitos.

Os oráculos vêm das páginas 221–225 da Language Reference IBM 6.4: sinal fixo,
inserção flutuante, supressão com Z ou *, ponto decimal e separadores. ZERO
em ZZZZ.99 resulta em quatro espaços e .00; em ZZZZ.ZZ, só espaços.
A implementação desta família ainda não está qualificada no corpus.

## Fechamento da obrigação de MOVE lógico

Uma prova completa de transferência da família textual satisfaz a obrigação
que o diagnóstico antigo chamava de identidade escalar. A capability parcial
(ao menos um receptor) não basta: todos os receptores precisam ter fitting e
armazenamento provados. Origem DATA exige famílias disjuntas, preservando a
incerteza de sobreposição. O oracle contrasta grupos completos com uma
sequência que contém receptor editado sem conversão provada. A checagem usa
os índices canônicos e uma passagem pelos receptores.

## Modificação de referência em coordenadas de caracteres

IBM Reference Modification define posição de origem 1 e comprimento opcional;
sem comprimento, o acesso termina no fim do item. A prova lógica publica o
intervalo relativo ao item, sem converter caracteres em bytes nem assumir CCSID.
Limites constantes precisam estar dentro do item. Acesso parcial nunca publica
wholeItemAccess/logicalWholeItem. O lower recorta a origem, faz fitting para o
receptor e atualiza a raiz e suas visões pelo mesmo caminho de escrita textual.
Oracles: ABCDEFGH12, A(3:2) := xy → ABxyEFGH12; A(8:) tem comprimento 1;
posição zero, extensão excedente e posição dinâmica não recebem prova constante.
Complexidade linear nos receptores e nas projeções realmente publicadas.
Fonte: https://www.ibm.com/docs/en/cobol-zos/6.3.0?topic=reference-modification

A análise textual agora é preparada uma vez depois dos tipos/células locais,
na `ScalarMoveSemantics`, e cobre famílias e células textuais fechadas com o
mesmo algoritmo de MOVE. A análise duplicada anterior em StorageAccessSemantics
foi removida. Fatias de aliases usam a identidade canônica de célula; não criam
uma nova alocação. A execução AIR do fixture misto resulta em ABxyEFGH tanto
pela referência original quanto por REDEFINES. A versão com CALL no receptor
publicou ABXYEFGH em dependencies.json.

Medição intermediária wave9: 73 fontes sem falha, 2.003 MOVEs ainda abertos
(2.207 antes desta etapa). Foram fechadas 173 obrigações já satisfeitas pela
família textual e 31 acessos parciais constantes. Os 3.807 usos de condições 88
e 1.819 atribuições SET permaneceram no produto. Pipeline ainda em qualificação.

A pipeline wave9 concluiu as 292 etapas. A comparação com CP2.1 mantém todos
os sites, arquivos e dependências qualificadas. Candidatos de programa passam
de 209 a 208: COSGN00C, no fallback de navegação de COPAUS0C, deixa de aparecer
na trajetória que já definiu COMEN01C. Os oráculos menu/espaços/desconhecido
confirmam a eliminação apenas do fallback inviável no primeiro cenário e a
preservação do sign-on nos outros dois. Os estados seguem 65 PARTIAL e
8 COMPLETE; nenhuma nova afirmação de completude do corpus é feita.

## Critério de conclusão atualizado em 2026-10-04

A orientação do usuário substitui o fechamento por contagem de gaps. O objetivo
é conservar dependências e influência local: reads de valor/endereço, efeitos
por receptor, MUST/MAY, aliases/ranges, source expression, identidade e provenance.
Valor ou configuração desconhecidos não exigem resultado concreto; missing COPY,
SQL INCLUDE e DCLGEN reais continuam explícitos. Não é exigido manifesto externo.

Sequência finita: estabilizar o diff; auditar transferências admitidas e corrigir
perdas de causa/efeito representáveis; classificar os MOVEs dos 73 fontes; qualificar
efeitos/RD focais, contratos, FAST e um corpus73/292 final comparável; alinhar pins
e publicar os PRs. Preservar a precisão já construída. Nova família exata exige
witness de dependência/efeito que uma abstração adequada não atende. Não implementar
AP, novo solver, checkpoint3 ou slicing interprograma. Zero gaps e COMPLETE
universal não são critérios de conclusão. Sem merge.

A medição wave9 e suas causas permanecem evidência histórica. A classificação
final distinguirá transferência precisa, abstrata causal, input de código ausente
e fronteira residual de modelagem; contagem isolada não mede recall de dependências.

## Fitting simbólico e identidade

O ajuste textual publica apenas regra, extensão e provenance. O lower usa
fit_text também para literais e conserva os bytes escritos do emissor; não
constrói padding no frontend, no contrato ou na admissão. Certificados antigos
são verificados na entrada em tempo linear no JSON efetivamente fornecido,
sem alocar a extensão declarada. Todos entram no mesmo modelo simbólico.
A prova redundante de resultados literais dos receptores lógicos deixa de ser
produzida; os receptores usam o caminho comum de fitting e atualização da família.
Oracle: PIC X(1000000000) com MOVE 'A' publica uma receita de tamanho constante.
Os exemplos pequenos conservam os resultados escritos à mão e são executados
sobre a AIR. Regras, extensão e campos extras forjados continuam sendo rejeitados.

A identidade de publicação inclui TRUNC, descritores de edição e intervalos
lógicos parciais. O teste RED demonstrou que TRUNC era ignorado. Os testes GREEN
verificam diferenças de identidade para mudanças semânticas nesses três fatos,
incluindo dois produtos admitidos que escrevem em posições diferentes.

## Geometria comprimida de OCCURS fixo

Uma tabela de repetição fixa ocupa contagem × extensão de um elemento. Essa
geometria permite provar que campos anteriores e posteriores não se sobrepõem
à tabela, sem expandir ocorrências e sem atribuir uma célula escalar à tabela.
A tabela entra na varredura como intervalo indisponível para células escalares;
qualquer alias que o intersecte continua bloqueado. Contagem variável e cláusulas
sem extensão provada conservam a obrigação. O algoritmo multiplica BigInteger
uma vez por declaração e mantém uma entrada por intervalo fonte.
Oracle: NAME-I/NAME-O e TAIL-I/TAIL-O, antes/depois de OCCURS 10 ou 1000000000,
continuam aliases exatos; PIC X cobrindo a tabela não vira célula independente.
Fonte: IBM Language Reference 6.4, OCCURS e REDEFINES.

Medição wave10: 73 fontes / 292 etapas sem falha; 1.989 MOVEs com obrigações
abertas, contra 2.003 na wave9. A geometria de tabelas fechou mais 14 ocorrências.
A comparação manteve exatamente a diferença de COSGN00C já explicada na wave8;
nenhum novo delta de dependência. Permanecem 150 sites/208 candidatos de programa,
391/378 de arquivo, 259/95 qualificados, 65 PARTIAL/8 COMPLETE e 3.807 usos de
condições 88 / 1.819 SETs.

A regressão do fitting foi composta pela suíte frontend de 1.415 testes (uma
expectativa histórica de campo removido corrigida e reexecutada) e pela suíte
lower até ZeroFill, seguida da suíte ZeroFill corrigida e de todas as execuções
restantes do POM. Os valores esperados não mudaram; os testes passaram a executar
fit_text em vez de exigir Literal. Logs: frontend-full-wave11,
symbolic-fitting-compatibility-green, lower-full-wave39 e
symbolic-fitting-lower-remaining. FAST final continua pendente.
