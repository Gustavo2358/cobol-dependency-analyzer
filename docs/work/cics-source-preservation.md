# Preservar dependências após controle CICS parcial

- id: CICS-SOURCE-PRESERVATION-001
- title: Manter possibilidades de continuação local em CICS não modelado
- status: IN_PROGRESS
- scope: possibilidades de fonte no frontend; contrato existente SP 2.52; E2E até dependencies.json

## Regra e decisão

A [IBM sobre tratamento de condições](https://www.ibm.com/docs/en/cics-ts/5.6.0?topic=conditions-using-push-handle-pop-handle-commands)
define que RESP ou NOHANDLE devolve controle à próxima instrução quando ocorre
uma condição. [RETURN](https://www.ibm.com/docs/en/cics-ts/6.x?topic=summary-return)
pode gerar CHANNELERR, INVREQ ou LENGERR, inclusive com CHANNEL e INPUTMSG.
A terminação no caminho de sucesso não prova que todos os caminhos terminam.

O frontend atual exclui todo RETURN/XCTL não modelado de `sourceContinuations`.
Isso elimina a qualificação do CALL seguinte mesmo com tratamento local explícito.
A reprodução até dependências termina COMPLETE, com a ocorrência inventariada
mas sem candidato. É um falso negativo do modelo de possibilidades da fonte.

Usar as opções canônicas já analisadas: NOHANDLE sem operando ou RESP com
operando não vazio permite uma hipótese CONTROL_POSSIBILITY para a continuação
simbólica já conhecida. A regra vale para gaps atuais e futuros nessas famílias;
não exige modelar cada opção antes de preservar a dependência. RESP2 isolado não
ativa tratamento local. ABEND, GOBACK, STOP e formas terminais sem essa evidência
continuam excluídos. Formas suportadas mantêm sua qualificação existente.

Garantia LANGUAGE_GUARANTEED: uma condição pode retornar localmente com RESP/NOHANDLE.
Garantia ARCHITECTURE_GUARANTEED: CONTROL_POSSIBILITY nunca autoriza AIR executável.
Não se afirma que uma condição necessariamente ocorrerá ou que o comando
desconhecido tem efeitos conhecidos. Gaps e UNKNOWN_LOCAL permanecem. O algoritmo
é linear no número de opções, sem nova análise de texto ou mudança de wire.

## Oráculos anteriores à implementação

- RETURN CHANNEL/INPUTMSG com RESP/NOHANDLE: CALL posterior qualificado como
  possibilidade da fonte; dependências PARTIAL; AIR preserva a fronteira.
- XCTL com opção desconhecida e RESP/NOHANDLE: mesma continuidade hipotética;
  alvo do próprio XCTL preservado. A opção fictícia é teste de gap futuro,
  não exemplo de sintaxe IBM válida.
- RESP2 isolado, flags malformadas e NOHANDLE dentro de literal não autorizam
  essa continuidade; RETURN simples, GOBACK, STOP e ABEND continuam terminais.
- Composição em IF/PERFORM, alvos literais/dinâmicos e arquivos: a continuidade
  usa o destino da gramática; não usa a posição incidental no JSON.
- Efeitos desconhecidos conservam candidatos condicionais; sobrescrita conhecida
  elimina o valor antigo; código comprovadamente morto não ganha candidato.

## Gates e limites da auditoria

Mudança de núcleo semântico com consumidores existentes: RED/GREEN focal,
famílias vizinhas de controle, FAST e pares selecionados nas CLIs de produção
até dependencies.json. A evidência anterior de REMARKS continua válida para a
normalização; controle e dependências afetados precisam de nova execução.

Os exemplos das fotos são de uma stack antiga. Na stack atual, os gaps de
FORMATTIME/DELETEQ já preservam possibilidades da fonte e PARTIAL. Não ampliar
o catálogo CICS para corrigir sintomas que já não se reproduzem.

PERFORM com literal zero foi detectado na auditoria, mas não é oracle de COBOL
válido: a [IBM](https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=statement-perform-times-phrase)
descreve literal positivo e explicita o desvio com zero/negativo para contagem
em identificador. Esse limite do perfil deve permanecer separado dos defeitos
confirmados com programas válidos. Nenhum compilador IBM foi executado.

## Resultado da qualificação

A correção reutiliza SP 2.52 e os consumidores existentes. Nenhum contrato,
modelo AIR ou filtro global de alcance foi alterado. Está pronta para revisão
junto com [REMARKS](remarks-area-a-compatibility.md), ainda sem integração.

- RED: três falhas em quatro testes; controles terminais/malformados passaram.
- GREEN focal: 34 testes, zero falhas/erros/skips.
- FAST antes da integração: 752 testes, zero falhas/erros/skips.
- 34 pares pelas quatro CLIs de produção: nenhum candidato anterior de programa
  ou arquivo removido. Dez casos recuperaram um alvo de programa cada; o caso
  de arquivo recuperou INPUTDAT nos usos OPEN, READ e CLOSE.
- Os quatro casos mínimos de CICS passaram de COMPLETE com alvo posterior
  ausente para PARTIAL com AFTERPGM. Nomes de programa têm oito caracteres.
- A AIR dos 34 pares é idêntica após normalizar somente a identidade da
  publicação, inclusive quando embutida em sourceKey. Hipóteses não viraram
  arestas executáveis. O caso morto continuou sem candidato.
- Os 18 casos não afetados da matriz inicial conservaram o JSON inteiro.
- Sete controles E2E de REMARKS foram reexecutados: JSON inteiro idêntico ao
  frontend final de REMARKS, incluindo SQLCA PARTIAL e o limite de `*>`.
- PERFORM com variável zero/negativa já preserva AFTERPGM no baseline e no fix;
  não houve mudança da regra de contagem. Sobrescrita comprovada manteve apenas
  o valor novo. Efeito desconhecido manteve o valor conhecido com premissas.

Evidência local: `artefatos-e2e/cics-preservation-20261002/`, especialmente
`comparison.json`, `check-results.py`, `runtime-after.json` e os produtos brutos.
Baseline frontend: `108e553d154b961f12da8d6068133d4f13aba983`.
Lower: `01096a2a4b7e103bb679f308c9df7bce34a45dd5`;
AIR: `6c4a6eb225fb4bb4fdc5871bc5232f03387ef099`;
CFG/dependências: `b3fed8de9994ce154c85191758775dbece09a85a`;
especificação AIR: `2c7f31f19efbe3211a2aea5bbda90173a9666fe2`.

## Política recomendada e limites

Preservar candidatos qualificados pela fonte, com as premissas e o remainder,
resolve esta classe sem completar todo o catálogo CICS. Retirar globalmente
UNREACHABLE_IN_MODEL apagaria a distinção entre um fluxo desconhecido e código
que o modelo demonstrou morto. A correção está na autoridade da qualificação.
O consumidor deve ler `dependencies.programs` e `dependencies.files`; `edges`
representa apenas a projeção executável e pode continuar vazio nesses casos.

Esta auditoria cobre 34 casos de políticas e sete regressões de REMARKS; não
prova ausência de outros gaps. Não expande tratamento de condições por handlers,
modelagem de efeitos CICS nem nomes fora do perfil. Os nove caracteres das fotos
são detalhe da fixture: o usuário confirmou oito caracteres no programa real,
e os contraexemplos confirmados nesta auditoria usam oito.

A qualification-local anterior de 1319 testes pertence à mudança REMARKS. Não
foi repetida como gate da correção CICS: as fronteiras alteradas foram exercitadas
pelo FAST, pelas famílias focais e pelos pares E2E. O corpus anterior de 96 casos
continua evidência de REMARKS, não é apresentado como nova execução deste fix.


## Integração com a main atualizada

A main `96e7ab2` incorporou o parser próprio opt-in durante a auditoria.
O merge na branch, `f9285170f954bdea232525bf72a54996c1627be4`, preservou a união
sem duplicatas das 124 suítes FAST, incluindo parser, REMARKS e CICS.

- FAST integrado: 775 testes, zero falhas/erros/skips.
- Os 41 casos finais foram executados novamente e mantiveram todo o JSON de
  dependências idêntico ao resultado já qualificado.
- Cinco casos de CICS/REMARKS também foram executados com `--parser direct-ast-lab`;
  os cinco JSONs ficaram idênticos ao modo padrão.
- Evidência: `integration-comparison.json`, `check-integration.py`,
  `integration-fast.log`, `runtime-integrated.json` e produtos brutos locais.
