      * Fixture reconstruida a partir dos trechos recuperados do video.
      * Sequencia inferida: random.Random(1), Python; 2000 campos e 150 paragrafos.
      * Os seis paragrafos disponiveis coincidem integralmente com esta sequencia.
      * Os demais foram reconstruidos; sua igualdade com o video nao foi conferida.
      * Formato livre. Mantidos PERFORM e GO TO cruzados, sem simplificar o fluxo.
       IDENTIFICATION DIVISION.
       PROGRAM-ID. FIXTURE02.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 WS-DATA.
           05 WS-V00000 PIC X(8).
           05 WS-V00001 PIC X(8).
           05 WS-V00002 PIC X(8).
           05 WS-V00003 PIC X(8).
           05 WS-V00004 PIC X(8).
           05 WS-V00005 PIC X(8).
           05 WS-V00006 PIC X(8).
           05 WS-V00007 PIC X(8).
           05 WS-V00008 PIC X(8).
           05 WS-V00009 PIC X(8).
           05 WS-V00010 PIC X(8).
           05 WS-V00011 PIC X(8).
           05 WS-V00012 PIC X(8).
           05 WS-V00013 PIC X(8).
           05 WS-V00014 PIC X(8).
           05 WS-V00015 PIC X(8).
           05 WS-V00016 PIC X(8).
           05 WS-V00017 PIC X(8).
           05 WS-V00018 PIC X(8).
           05 WS-V00019 PIC X(8).
           05 WS-V00020 PIC X(8).
           05 WS-V00021 PIC X(8).
           05 WS-V00022 PIC X(8).
           05 WS-V00023 PIC X(8).
           05 WS-V00024 PIC X(8).
           05 WS-V00025 PIC X(8).
           05 WS-V00026 PIC X(8).
           05 WS-V00027 PIC X(8).
           05 WS-V00028 PIC X(8).
           05 WS-V00029 PIC X(8).
           05 WS-V00030 PIC X(8).
           05 WS-V00031 PIC X(8).
           05 WS-V00032 PIC X(8).
           05 WS-V00033 PIC X(8).
           05 WS-V00034 PIC X(8).
           05 WS-V00035 PIC X(8).
           05 WS-V00036 PIC X(8).
           05 WS-V00037 PIC X(8).
           05 WS-V00038 PIC X(8).
           05 WS-V00039 PIC X(8).
           05 WS-V00040 PIC X(8).
           05 WS-V00041 PIC X(8).
           05 WS-V00042 PIC X(8).
           05 WS-V00043 PIC X(8).
           05 WS-V00044 PIC X(8).
           05 WS-V00045 PIC X(8).
           05 WS-V00046 PIC X(8).
           05 WS-V00047 PIC X(8).
           05 WS-V00048 PIC X(8).
           05 WS-V00049 PIC X(8).
           05 WS-V00050 PIC X(8).
           05 WS-V00051 PIC X(8).
           05 WS-V00052 PIC X(8).
           05 WS-V00053 PIC X(8).
           05 WS-V00054 PIC X(8).
           05 WS-V00055 PIC X(8).
           05 WS-V00056 PIC X(8).
           05 WS-V00057 PIC X(8).
           05 WS-V00058 PIC X(8).
           05 WS-V00059 PIC X(8).
           05 WS-V00060 PIC X(8).
           05 WS-V00061 PIC X(8).
           05 WS-V00062 PIC X(8).
           05 WS-V00063 PIC X(8).
           05 WS-V00064 PIC X(8).
           05 WS-V00065 PIC X(8).
           05 WS-V00066 PIC X(8).
           05 WS-V00067 PIC X(8).
           05 WS-V00068 PIC X(8).
           05 WS-V00069 PIC X(8).
           05 WS-V00070 PIC X(8).
           05 WS-V00071 PIC X(8).
           05 WS-V00072 PIC X(8).
           05 WS-V00073 PIC X(8).
           05 WS-V00074 PIC X(8).
           05 WS-V00075 PIC X(8).
           05 WS-V00076 PIC X(8).
           05 WS-V00077 PIC X(8).
           05 WS-V00078 PIC X(8).
           05 WS-V00079 PIC X(8).
           05 WS-V00080 PIC X(8).
           05 WS-V00081 PIC X(8).
           05 WS-V00082 PIC X(8).
           05 WS-V00083 PIC X(8).
           05 WS-V00084 PIC X(8).
           05 WS-V00085 PIC X(8).
           05 WS-V00086 PIC X(8).
           05 WS-V00087 PIC X(8).
           05 WS-V00088 PIC X(8).
           05 WS-V00089 PIC X(8).
           05 WS-V00090 PIC X(8).
           05 WS-V00091 PIC X(8).
           05 WS-V00092 PIC X(8).
           05 WS-V00093 PIC X(8).
           05 WS-V00094 PIC X(8).
           05 WS-V00095 PIC X(8).
           05 WS-V00096 PIC X(8).
           05 WS-V00097 PIC X(8).
           05 WS-V00098 PIC X(8).
           05 WS-V00099 PIC X(8).
           05 WS-V00100 PIC X(8).
           05 WS-V00101 PIC X(8).
           05 WS-V00102 PIC X(8).
           05 WS-V00103 PIC X(8).
           05 WS-V00104 PIC X(8).
           05 WS-V00105 PIC X(8).
           05 WS-V00106 PIC X(8).
           05 WS-V00107 PIC X(8).
           05 WS-V00108 PIC X(8).
           05 WS-V00109 PIC X(8).
           05 WS-V00110 PIC X(8).
           05 WS-V00111 PIC X(8).
           05 WS-V00112 PIC X(8).
           05 WS-V00113 PIC X(8).
           05 WS-V00114 PIC X(8).
           05 WS-V00115 PIC X(8).
           05 WS-V00116 PIC X(8).
           05 WS-V00117 PIC X(8).
           05 WS-V00118 PIC X(8).
           05 WS-V00119 PIC X(8).
           05 WS-V00120 PIC X(8).
           05 WS-V00121 PIC X(8).
           05 WS-V00122 PIC X(8).
           05 WS-V00123 PIC X(8).
           05 WS-V00124 PIC X(8).
           05 WS-V00125 PIC X(8).
           05 WS-V00126 PIC X(8).
           05 WS-V00127 PIC X(8).
           05 WS-V00128 PIC X(8).
           05 WS-V00129 PIC X(8).
           05 WS-V00130 PIC X(8).
           05 WS-V00131 PIC X(8).
           05 WS-V00132 PIC X(8).
           05 WS-V00133 PIC X(8).
           05 WS-V00134 PIC X(8).
           05 WS-V00135 PIC X(8).
           05 WS-V00136 PIC X(8).
           05 WS-V00137 PIC X(8).
           05 WS-V00138 PIC X(8).
           05 WS-V00139 PIC X(8).
           05 WS-V00140 PIC X(8).
           05 WS-V00141 PIC X(8).
           05 WS-V00142 PIC X(8).
           05 WS-V00143 PIC X(8).
           05 WS-V00144 PIC X(8).
           05 WS-V00145 PIC X(8).
           05 WS-V00146 PIC X(8).
           05 WS-V00147 PIC X(8).
           05 WS-V00148 PIC X(8).
           05 WS-V00149 PIC X(8).
           05 WS-V00150 PIC X(8).
           05 WS-V00151 PIC X(8).
           05 WS-V00152 PIC X(8).
           05 WS-V00153 PIC X(8).
           05 WS-V00154 PIC X(8).
           05 WS-V00155 PIC X(8).
           05 WS-V00156 PIC X(8).
           05 WS-V00157 PIC X(8).
           05 WS-V00158 PIC X(8).
           05 WS-V00159 PIC X(8).
           05 WS-V00160 PIC X(8).
           05 WS-V00161 PIC X(8).
           05 WS-V00162 PIC X(8).
           05 WS-V00163 PIC X(8).
           05 WS-V00164 PIC X(8).
           05 WS-V00165 PIC X(8).
           05 WS-V00166 PIC X(8).
           05 WS-V00167 PIC X(8).
           05 WS-V00168 PIC X(8).
           05 WS-V00169 PIC X(8).
           05 WS-V00170 PIC X(8).
           05 WS-V00171 PIC X(8).
           05 WS-V00172 PIC X(8).
           05 WS-V00173 PIC X(8).
           05 WS-V00174 PIC X(8).
           05 WS-V00175 PIC X(8).
           05 WS-V00176 PIC X(8).
           05 WS-V00177 PIC X(8).
           05 WS-V00178 PIC X(8).
           05 WS-V00179 PIC X(8).
           05 WS-V00180 PIC X(8).
           05 WS-V00181 PIC X(8).
           05 WS-V00182 PIC X(8).
           05 WS-V00183 PIC X(8).
           05 WS-V00184 PIC X(8).
           05 WS-V00185 PIC X(8).
           05 WS-V00186 PIC X(8).
           05 WS-V00187 PIC X(8).
           05 WS-V00188 PIC X(8).
           05 WS-V00189 PIC X(8).
           05 WS-V00190 PIC X(8).
           05 WS-V00191 PIC X(8).
           05 WS-V00192 PIC X(8).
           05 WS-V00193 PIC X(8).
           05 WS-V00194 PIC X(8).
           05 WS-V00195 PIC X(8).
           05 WS-V00196 PIC X(8).
           05 WS-V00197 PIC X(8).
           05 WS-V00198 PIC X(8).
           05 WS-V00199 PIC X(8).
           05 WS-V00200 PIC X(8).
           05 WS-V00201 PIC X(8).
           05 WS-V00202 PIC X(8).
           05 WS-V00203 PIC X(8).
           05 WS-V00204 PIC X(8).
           05 WS-V00205 PIC X(8).
           05 WS-V00206 PIC X(8).
           05 WS-V00207 PIC X(8).
           05 WS-V00208 PIC X(8).
           05 WS-V00209 PIC X(8).
           05 WS-V00210 PIC X(8).
           05 WS-V00211 PIC X(8).
           05 WS-V00212 PIC X(8).
           05 WS-V00213 PIC X(8).
           05 WS-V00214 PIC X(8).
           05 WS-V00215 PIC X(8).
           05 WS-V00216 PIC X(8).
           05 WS-V00217 PIC X(8).
           05 WS-V00218 PIC X(8).
           05 WS-V00219 PIC X(8).
           05 WS-V00220 PIC X(8).
           05 WS-V00221 PIC X(8).
           05 WS-V00222 PIC X(8).
           05 WS-V00223 PIC X(8).
           05 WS-V00224 PIC X(8).
           05 WS-V00225 PIC X(8).
           05 WS-V00226 PIC X(8).
           05 WS-V00227 PIC X(8).
           05 WS-V00228 PIC X(8).
           05 WS-V00229 PIC X(8).
           05 WS-V00230 PIC X(8).
           05 WS-V00231 PIC X(8).
           05 WS-V00232 PIC X(8).
           05 WS-V00233 PIC X(8).
           05 WS-V00234 PIC X(8).
           05 WS-V00235 PIC X(8).
           05 WS-V00236 PIC X(8).
           05 WS-V00237 PIC X(8).
           05 WS-V00238 PIC X(8).
           05 WS-V00239 PIC X(8).
           05 WS-V00240 PIC X(8).
           05 WS-V00241 PIC X(8).
           05 WS-V00242 PIC X(8).
           05 WS-V00243 PIC X(8).
           05 WS-V00244 PIC X(8).
           05 WS-V00245 PIC X(8).
           05 WS-V00246 PIC X(8).
           05 WS-V00247 PIC X(8).
           05 WS-V00248 PIC X(8).
           05 WS-V00249 PIC X(8).
           05 WS-V00250 PIC X(8).
           05 WS-V00251 PIC X(8).
           05 WS-V00252 PIC X(8).
           05 WS-V00253 PIC X(8).
           05 WS-V00254 PIC X(8).
           05 WS-V00255 PIC X(8).
           05 WS-V00256 PIC X(8).
           05 WS-V00257 PIC X(8).
           05 WS-V00258 PIC X(8).
           05 WS-V00259 PIC X(8).
           05 WS-V00260 PIC X(8).
           05 WS-V00261 PIC X(8).
           05 WS-V00262 PIC X(8).
           05 WS-V00263 PIC X(8).
           05 WS-V00264 PIC X(8).
           05 WS-V00265 PIC X(8).
           05 WS-V00266 PIC X(8).
           05 WS-V00267 PIC X(8).
           05 WS-V00268 PIC X(8).
           05 WS-V00269 PIC X(8).
           05 WS-V00270 PIC X(8).
           05 WS-V00271 PIC X(8).
           05 WS-V00272 PIC X(8).
           05 WS-V00273 PIC X(8).
           05 WS-V00274 PIC X(8).
           05 WS-V00275 PIC X(8).
           05 WS-V00276 PIC X(8).
           05 WS-V00277 PIC X(8).
           05 WS-V00278 PIC X(8).
           05 WS-V00279 PIC X(8).
           05 WS-V00280 PIC X(8).
           05 WS-V00281 PIC X(8).
           05 WS-V00282 PIC X(8).
           05 WS-V00283 PIC X(8).
           05 WS-V00284 PIC X(8).
           05 WS-V00285 PIC X(8).
           05 WS-V00286 PIC X(8).
           05 WS-V00287 PIC X(8).
           05 WS-V00288 PIC X(8).
           05 WS-V00289 PIC X(8).
           05 WS-V00290 PIC X(8).
           05 WS-V00291 PIC X(8).
           05 WS-V00292 PIC X(8).
           05 WS-V00293 PIC X(8).
           05 WS-V00294 PIC X(8).
           05 WS-V00295 PIC X(8).
           05 WS-V00296 PIC X(8).
           05 WS-V00297 PIC X(8).
           05 WS-V00298 PIC X(8).
           05 WS-V00299 PIC X(8).
           05 WS-V00300 PIC X(8).
           05 WS-V00301 PIC X(8).
           05 WS-V00302 PIC X(8).
           05 WS-V00303 PIC X(8).
           05 WS-V00304 PIC X(8).
           05 WS-V00305 PIC X(8).
           05 WS-V00306 PIC X(8).
           05 WS-V00307 PIC X(8).
           05 WS-V00308 PIC X(8).
           05 WS-V00309 PIC X(8).
           05 WS-V00310 PIC X(8).
           05 WS-V00311 PIC X(8).
           05 WS-V00312 PIC X(8).
           05 WS-V00313 PIC X(8).
           05 WS-V00314 PIC X(8).
           05 WS-V00315 PIC X(8).
           05 WS-V00316 PIC X(8).
           05 WS-V00317 PIC X(8).
           05 WS-V00318 PIC X(8).
           05 WS-V00319 PIC X(8).
           05 WS-V00320 PIC X(8).
           05 WS-V00321 PIC X(8).
           05 WS-V00322 PIC X(8).
           05 WS-V00323 PIC X(8).
           05 WS-V00324 PIC X(8).
           05 WS-V00325 PIC X(8).
           05 WS-V00326 PIC X(8).
           05 WS-V00327 PIC X(8).
           05 WS-V00328 PIC X(8).
           05 WS-V00329 PIC X(8).
           05 WS-V00330 PIC X(8).
           05 WS-V00331 PIC X(8).
           05 WS-V00332 PIC X(8).
           05 WS-V00333 PIC X(8).
           05 WS-V00334 PIC X(8).
           05 WS-V00335 PIC X(8).
           05 WS-V00336 PIC X(8).
           05 WS-V00337 PIC X(8).
           05 WS-V00338 PIC X(8).
           05 WS-V00339 PIC X(8).
           05 WS-V00340 PIC X(8).
           05 WS-V00341 PIC X(8).
           05 WS-V00342 PIC X(8).
           05 WS-V00343 PIC X(8).
           05 WS-V00344 PIC X(8).
           05 WS-V00345 PIC X(8).
           05 WS-V00346 PIC X(8).
           05 WS-V00347 PIC X(8).
           05 WS-V00348 PIC X(8).
           05 WS-V00349 PIC X(8).
           05 WS-V00350 PIC X(8).
           05 WS-V00351 PIC X(8).
           05 WS-V00352 PIC X(8).
           05 WS-V00353 PIC X(8).
           05 WS-V00354 PIC X(8).
           05 WS-V00355 PIC X(8).
           05 WS-V00356 PIC X(8).
           05 WS-V00357 PIC X(8).
           05 WS-V00358 PIC X(8).
           05 WS-V00359 PIC X(8).
           05 WS-V00360 PIC X(8).
           05 WS-V00361 PIC X(8).
           05 WS-V00362 PIC X(8).
           05 WS-V00363 PIC X(8).
           05 WS-V00364 PIC X(8).
           05 WS-V00365 PIC X(8).
           05 WS-V00366 PIC X(8).
           05 WS-V00367 PIC X(8).
           05 WS-V00368 PIC X(8).
           05 WS-V00369 PIC X(8).
           05 WS-V00370 PIC X(8).
           05 WS-V00371 PIC X(8).
           05 WS-V00372 PIC X(8).
           05 WS-V00373 PIC X(8).
           05 WS-V00374 PIC X(8).
           05 WS-V00375 PIC X(8).
           05 WS-V00376 PIC X(8).
           05 WS-V00377 PIC X(8).
           05 WS-V00378 PIC X(8).
           05 WS-V00379 PIC X(8).
           05 WS-V00380 PIC X(8).
           05 WS-V00381 PIC X(8).
           05 WS-V00382 PIC X(8).
           05 WS-V00383 PIC X(8).
           05 WS-V00384 PIC X(8).
           05 WS-V00385 PIC X(8).
           05 WS-V00386 PIC X(8).
           05 WS-V00387 PIC X(8).
           05 WS-V00388 PIC X(8).
           05 WS-V00389 PIC X(8).
           05 WS-V00390 PIC X(8).
           05 WS-V00391 PIC X(8).
           05 WS-V00392 PIC X(8).
           05 WS-V00393 PIC X(8).
           05 WS-V00394 PIC X(8).
           05 WS-V00395 PIC X(8).
           05 WS-V00396 PIC X(8).
           05 WS-V00397 PIC X(8).
           05 WS-V00398 PIC X(8).
           05 WS-V00399 PIC X(8).
           05 WS-V00400 PIC X(8).
           05 WS-V00401 PIC X(8).
           05 WS-V00402 PIC X(8).
           05 WS-V00403 PIC X(8).
           05 WS-V00404 PIC X(8).
           05 WS-V00405 PIC X(8).
           05 WS-V00406 PIC X(8).
           05 WS-V00407 PIC X(8).
           05 WS-V00408 PIC X(8).
           05 WS-V00409 PIC X(8).
           05 WS-V00410 PIC X(8).
           05 WS-V00411 PIC X(8).
           05 WS-V00412 PIC X(8).
           05 WS-V00413 PIC X(8).
           05 WS-V00414 PIC X(8).
           05 WS-V00415 PIC X(8).
           05 WS-V00416 PIC X(8).
           05 WS-V00417 PIC X(8).
           05 WS-V00418 PIC X(8).
           05 WS-V00419 PIC X(8).
           05 WS-V00420 PIC X(8).
           05 WS-V00421 PIC X(8).
           05 WS-V00422 PIC X(8).
           05 WS-V00423 PIC X(8).
           05 WS-V00424 PIC X(8).
           05 WS-V00425 PIC X(8).
           05 WS-V00426 PIC X(8).
           05 WS-V00427 PIC X(8).
           05 WS-V00428 PIC X(8).
           05 WS-V00429 PIC X(8).
           05 WS-V00430 PIC X(8).
           05 WS-V00431 PIC X(8).
           05 WS-V00432 PIC X(8).
           05 WS-V00433 PIC X(8).
           05 WS-V00434 PIC X(8).
           05 WS-V00435 PIC X(8).
           05 WS-V00436 PIC X(8).
           05 WS-V00437 PIC X(8).
           05 WS-V00438 PIC X(8).
           05 WS-V00439 PIC X(8).
           05 WS-V00440 PIC X(8).
           05 WS-V00441 PIC X(8).
           05 WS-V00442 PIC X(8).
           05 WS-V00443 PIC X(8).
           05 WS-V00444 PIC X(8).
           05 WS-V00445 PIC X(8).
           05 WS-V00446 PIC X(8).
           05 WS-V00447 PIC X(8).
           05 WS-V00448 PIC X(8).
           05 WS-V00449 PIC X(8).
           05 WS-V00450 PIC X(8).
           05 WS-V00451 PIC X(8).
           05 WS-V00452 PIC X(8).
           05 WS-V00453 PIC X(8).
           05 WS-V00454 PIC X(8).
           05 WS-V00455 PIC X(8).
           05 WS-V00456 PIC X(8).
           05 WS-V00457 PIC X(8).
           05 WS-V00458 PIC X(8).
           05 WS-V00459 PIC X(8).
           05 WS-V00460 PIC X(8).
           05 WS-V00461 PIC X(8).
           05 WS-V00462 PIC X(8).
           05 WS-V00463 PIC X(8).
           05 WS-V00464 PIC X(8).
           05 WS-V00465 PIC X(8).
           05 WS-V00466 PIC X(8).
           05 WS-V00467 PIC X(8).
           05 WS-V00468 PIC X(8).
           05 WS-V00469 PIC X(8).
           05 WS-V00470 PIC X(8).
           05 WS-V00471 PIC X(8).
           05 WS-V00472 PIC X(8).
           05 WS-V00473 PIC X(8).
           05 WS-V00474 PIC X(8).
           05 WS-V00475 PIC X(8).
           05 WS-V00476 PIC X(8).
           05 WS-V00477 PIC X(8).
           05 WS-V00478 PIC X(8).
           05 WS-V00479 PIC X(8).
           05 WS-V00480 PIC X(8).
           05 WS-V00481 PIC X(8).
           05 WS-V00482 PIC X(8).
           05 WS-V00483 PIC X(8).
           05 WS-V00484 PIC X(8).
           05 WS-V00485 PIC X(8).
           05 WS-V00486 PIC X(8).
           05 WS-V00487 PIC X(8).
           05 WS-V00488 PIC X(8).
           05 WS-V00489 PIC X(8).
           05 WS-V00490 PIC X(8).
           05 WS-V00491 PIC X(8).
           05 WS-V00492 PIC X(8).
           05 WS-V00493 PIC X(8).
           05 WS-V00494 PIC X(8).
           05 WS-V00495 PIC X(8).
           05 WS-V00496 PIC X(8).
           05 WS-V00497 PIC X(8).
           05 WS-V00498 PIC X(8).
           05 WS-V00499 PIC X(8).
           05 WS-V00500 PIC X(8).
           05 WS-V00501 PIC X(8).
           05 WS-V00502 PIC X(8).
           05 WS-V00503 PIC X(8).
           05 WS-V00504 PIC X(8).
           05 WS-V00505 PIC X(8).
           05 WS-V00506 PIC X(8).
           05 WS-V00507 PIC X(8).
           05 WS-V00508 PIC X(8).
           05 WS-V00509 PIC X(8).
           05 WS-V00510 PIC X(8).
           05 WS-V00511 PIC X(8).
           05 WS-V00512 PIC X(8).
           05 WS-V00513 PIC X(8).
           05 WS-V00514 PIC X(8).
           05 WS-V00515 PIC X(8).
           05 WS-V00516 PIC X(8).
           05 WS-V00517 PIC X(8).
           05 WS-V00518 PIC X(8).
           05 WS-V00519 PIC X(8).
           05 WS-V00520 PIC X(8).
           05 WS-V00521 PIC X(8).
           05 WS-V00522 PIC X(8).
           05 WS-V00523 PIC X(8).
           05 WS-V00524 PIC X(8).
           05 WS-V00525 PIC X(8).
           05 WS-V00526 PIC X(8).
           05 WS-V00527 PIC X(8).
           05 WS-V00528 PIC X(8).
           05 WS-V00529 PIC X(8).
           05 WS-V00530 PIC X(8).
           05 WS-V00531 PIC X(8).
           05 WS-V00532 PIC X(8).
           05 WS-V00533 PIC X(8).
           05 WS-V00534 PIC X(8).
           05 WS-V00535 PIC X(8).
           05 WS-V00536 PIC X(8).
           05 WS-V00537 PIC X(8).
           05 WS-V00538 PIC X(8).
           05 WS-V00539 PIC X(8).
           05 WS-V00540 PIC X(8).
           05 WS-V00541 PIC X(8).
           05 WS-V00542 PIC X(8).
           05 WS-V00543 PIC X(8).
           05 WS-V00544 PIC X(8).
           05 WS-V00545 PIC X(8).
           05 WS-V00546 PIC X(8).
           05 WS-V00547 PIC X(8).
           05 WS-V00548 PIC X(8).
           05 WS-V00549 PIC X(8).
           05 WS-V00550 PIC X(8).
           05 WS-V00551 PIC X(8).
           05 WS-V00552 PIC X(8).
           05 WS-V00553 PIC X(8).
           05 WS-V00554 PIC X(8).
           05 WS-V00555 PIC X(8).
           05 WS-V00556 PIC X(8).
           05 WS-V00557 PIC X(8).
           05 WS-V00558 PIC X(8).
           05 WS-V00559 PIC X(8).
           05 WS-V00560 PIC X(8).
           05 WS-V00561 PIC X(8).
           05 WS-V00562 PIC X(8).
           05 WS-V00563 PIC X(8).
           05 WS-V00564 PIC X(8).
           05 WS-V00565 PIC X(8).
           05 WS-V00566 PIC X(8).
           05 WS-V00567 PIC X(8).
           05 WS-V00568 PIC X(8).
           05 WS-V00569 PIC X(8).
           05 WS-V00570 PIC X(8).
           05 WS-V00571 PIC X(8).
           05 WS-V00572 PIC X(8).
           05 WS-V00573 PIC X(8).
           05 WS-V00574 PIC X(8).
           05 WS-V00575 PIC X(8).
           05 WS-V00576 PIC X(8).
           05 WS-V00577 PIC X(8).
           05 WS-V00578 PIC X(8).
           05 WS-V00579 PIC X(8).
           05 WS-V00580 PIC X(8).
           05 WS-V00581 PIC X(8).
           05 WS-V00582 PIC X(8).
           05 WS-V00583 PIC X(8).
           05 WS-V00584 PIC X(8).
           05 WS-V00585 PIC X(8).
           05 WS-V00586 PIC X(8).
           05 WS-V00587 PIC X(8).
           05 WS-V00588 PIC X(8).
           05 WS-V00589 PIC X(8).
           05 WS-V00590 PIC X(8).
           05 WS-V00591 PIC X(8).
           05 WS-V00592 PIC X(8).
           05 WS-V00593 PIC X(8).
           05 WS-V00594 PIC X(8).
           05 WS-V00595 PIC X(8).
           05 WS-V00596 PIC X(8).
           05 WS-V00597 PIC X(8).
           05 WS-V00598 PIC X(8).
           05 WS-V00599 PIC X(8).
           05 WS-V00600 PIC X(8).
           05 WS-V00601 PIC X(8).
           05 WS-V00602 PIC X(8).
           05 WS-V00603 PIC X(8).
           05 WS-V00604 PIC X(8).
           05 WS-V00605 PIC X(8).
           05 WS-V00606 PIC X(8).
           05 WS-V00607 PIC X(8).
           05 WS-V00608 PIC X(8).
           05 WS-V00609 PIC X(8).
           05 WS-V00610 PIC X(8).
           05 WS-V00611 PIC X(8).
           05 WS-V00612 PIC X(8).
           05 WS-V00613 PIC X(8).
           05 WS-V00614 PIC X(8).
           05 WS-V00615 PIC X(8).
           05 WS-V00616 PIC X(8).
           05 WS-V00617 PIC X(8).
           05 WS-V00618 PIC X(8).
           05 WS-V00619 PIC X(8).
           05 WS-V00620 PIC X(8).
           05 WS-V00621 PIC X(8).
           05 WS-V00622 PIC X(8).
           05 WS-V00623 PIC X(8).
           05 WS-V00624 PIC X(8).
           05 WS-V00625 PIC X(8).
           05 WS-V00626 PIC X(8).
           05 WS-V00627 PIC X(8).
           05 WS-V00628 PIC X(8).
           05 WS-V00629 PIC X(8).
           05 WS-V00630 PIC X(8).
           05 WS-V00631 PIC X(8).
           05 WS-V00632 PIC X(8).
           05 WS-V00633 PIC X(8).
           05 WS-V00634 PIC X(8).
           05 WS-V00635 PIC X(8).
           05 WS-V00636 PIC X(8).
           05 WS-V00637 PIC X(8).
           05 WS-V00638 PIC X(8).
           05 WS-V00639 PIC X(8).
           05 WS-V00640 PIC X(8).
           05 WS-V00641 PIC X(8).
           05 WS-V00642 PIC X(8).
           05 WS-V00643 PIC X(8).
           05 WS-V00644 PIC X(8).
           05 WS-V00645 PIC X(8).
           05 WS-V00646 PIC X(8).
           05 WS-V00647 PIC X(8).
           05 WS-V00648 PIC X(8).
           05 WS-V00649 PIC X(8).
           05 WS-V00650 PIC X(8).
           05 WS-V00651 PIC X(8).
           05 WS-V00652 PIC X(8).
           05 WS-V00653 PIC X(8).
           05 WS-V00654 PIC X(8).
           05 WS-V00655 PIC X(8).
           05 WS-V00656 PIC X(8).
           05 WS-V00657 PIC X(8).
           05 WS-V00658 PIC X(8).
           05 WS-V00659 PIC X(8).
           05 WS-V00660 PIC X(8).
           05 WS-V00661 PIC X(8).
           05 WS-V00662 PIC X(8).
           05 WS-V00663 PIC X(8).
           05 WS-V00664 PIC X(8).
           05 WS-V00665 PIC X(8).
           05 WS-V00666 PIC X(8).
           05 WS-V00667 PIC X(8).
           05 WS-V00668 PIC X(8).
           05 WS-V00669 PIC X(8).
           05 WS-V00670 PIC X(8).
           05 WS-V00671 PIC X(8).
           05 WS-V00672 PIC X(8).
           05 WS-V00673 PIC X(8).
           05 WS-V00674 PIC X(8).
           05 WS-V00675 PIC X(8).
           05 WS-V00676 PIC X(8).
           05 WS-V00677 PIC X(8).
           05 WS-V00678 PIC X(8).
           05 WS-V00679 PIC X(8).
           05 WS-V00680 PIC X(8).
           05 WS-V00681 PIC X(8).
           05 WS-V00682 PIC X(8).
           05 WS-V00683 PIC X(8).
           05 WS-V00684 PIC X(8).
           05 WS-V00685 PIC X(8).
           05 WS-V00686 PIC X(8).
           05 WS-V00687 PIC X(8).
           05 WS-V00688 PIC X(8).
           05 WS-V00689 PIC X(8).
           05 WS-V00690 PIC X(8).
           05 WS-V00691 PIC X(8).
           05 WS-V00692 PIC X(8).
           05 WS-V00693 PIC X(8).
           05 WS-V00694 PIC X(8).
           05 WS-V00695 PIC X(8).
           05 WS-V00696 PIC X(8).
           05 WS-V00697 PIC X(8).
           05 WS-V00698 PIC X(8).
           05 WS-V00699 PIC X(8).
           05 WS-V00700 PIC X(8).
           05 WS-V00701 PIC X(8).
           05 WS-V00702 PIC X(8).
           05 WS-V00703 PIC X(8).
           05 WS-V00704 PIC X(8).
           05 WS-V00705 PIC X(8).
           05 WS-V00706 PIC X(8).
           05 WS-V00707 PIC X(8).
           05 WS-V00708 PIC X(8).
           05 WS-V00709 PIC X(8).
           05 WS-V00710 PIC X(8).
           05 WS-V00711 PIC X(8).
           05 WS-V00712 PIC X(8).
           05 WS-V00713 PIC X(8).
           05 WS-V00714 PIC X(8).
           05 WS-V00715 PIC X(8).
           05 WS-V00716 PIC X(8).
           05 WS-V00717 PIC X(8).
           05 WS-V00718 PIC X(8).
           05 WS-V00719 PIC X(8).
           05 WS-V00720 PIC X(8).
           05 WS-V00721 PIC X(8).
           05 WS-V00722 PIC X(8).
           05 WS-V00723 PIC X(8).
           05 WS-V00724 PIC X(8).
           05 WS-V00725 PIC X(8).
           05 WS-V00726 PIC X(8).
           05 WS-V00727 PIC X(8).
           05 WS-V00728 PIC X(8).
           05 WS-V00729 PIC X(8).
           05 WS-V00730 PIC X(8).
           05 WS-V00731 PIC X(8).
           05 WS-V00732 PIC X(8).
           05 WS-V00733 PIC X(8).
           05 WS-V00734 PIC X(8).
           05 WS-V00735 PIC X(8).
           05 WS-V00736 PIC X(8).
           05 WS-V00737 PIC X(8).
           05 WS-V00738 PIC X(8).
           05 WS-V00739 PIC X(8).
           05 WS-V00740 PIC X(8).
           05 WS-V00741 PIC X(8).
           05 WS-V00742 PIC X(8).
           05 WS-V00743 PIC X(8).
           05 WS-V00744 PIC X(8).
           05 WS-V00745 PIC X(8).
           05 WS-V00746 PIC X(8).
           05 WS-V00747 PIC X(8).
           05 WS-V00748 PIC X(8).
           05 WS-V00749 PIC X(8).
           05 WS-V00750 PIC X(8).
           05 WS-V00751 PIC X(8).
           05 WS-V00752 PIC X(8).
           05 WS-V00753 PIC X(8).
           05 WS-V00754 PIC X(8).
           05 WS-V00755 PIC X(8).
           05 WS-V00756 PIC X(8).
           05 WS-V00757 PIC X(8).
           05 WS-V00758 PIC X(8).
           05 WS-V00759 PIC X(8).
           05 WS-V00760 PIC X(8).
           05 WS-V00761 PIC X(8).
           05 WS-V00762 PIC X(8).
           05 WS-V00763 PIC X(8).
           05 WS-V00764 PIC X(8).
           05 WS-V00765 PIC X(8).
           05 WS-V00766 PIC X(8).
           05 WS-V00767 PIC X(8).
           05 WS-V00768 PIC X(8).
           05 WS-V00769 PIC X(8).
           05 WS-V00770 PIC X(8).
           05 WS-V00771 PIC X(8).
           05 WS-V00772 PIC X(8).
           05 WS-V00773 PIC X(8).
           05 WS-V00774 PIC X(8).
           05 WS-V00775 PIC X(8).
           05 WS-V00776 PIC X(8).
           05 WS-V00777 PIC X(8).
           05 WS-V00778 PIC X(8).
           05 WS-V00779 PIC X(8).
           05 WS-V00780 PIC X(8).
           05 WS-V00781 PIC X(8).
           05 WS-V00782 PIC X(8).
           05 WS-V00783 PIC X(8).
           05 WS-V00784 PIC X(8).
           05 WS-V00785 PIC X(8).
           05 WS-V00786 PIC X(8).
           05 WS-V00787 PIC X(8).
           05 WS-V00788 PIC X(8).
           05 WS-V00789 PIC X(8).
           05 WS-V00790 PIC X(8).
           05 WS-V00791 PIC X(8).
           05 WS-V00792 PIC X(8).
           05 WS-V00793 PIC X(8).
           05 WS-V00794 PIC X(8).
           05 WS-V00795 PIC X(8).
           05 WS-V00796 PIC X(8).
           05 WS-V00797 PIC X(8).
           05 WS-V00798 PIC X(8).
           05 WS-V00799 PIC X(8).
           05 WS-V00800 PIC X(8).
           05 WS-V00801 PIC X(8).
           05 WS-V00802 PIC X(8).
           05 WS-V00803 PIC X(8).
           05 WS-V00804 PIC X(8).
           05 WS-V00805 PIC X(8).
           05 WS-V00806 PIC X(8).
           05 WS-V00807 PIC X(8).
           05 WS-V00808 PIC X(8).
           05 WS-V00809 PIC X(8).
           05 WS-V00810 PIC X(8).
           05 WS-V00811 PIC X(8).
           05 WS-V00812 PIC X(8).
           05 WS-V00813 PIC X(8).
           05 WS-V00814 PIC X(8).
           05 WS-V00815 PIC X(8).
           05 WS-V00816 PIC X(8).
           05 WS-V00817 PIC X(8).
           05 WS-V00818 PIC X(8).
           05 WS-V00819 PIC X(8).
           05 WS-V00820 PIC X(8).
           05 WS-V00821 PIC X(8).
           05 WS-V00822 PIC X(8).
           05 WS-V00823 PIC X(8).
           05 WS-V00824 PIC X(8).
           05 WS-V00825 PIC X(8).
           05 WS-V00826 PIC X(8).
           05 WS-V00827 PIC X(8).
           05 WS-V00828 PIC X(8).
           05 WS-V00829 PIC X(8).
           05 WS-V00830 PIC X(8).
           05 WS-V00831 PIC X(8).
           05 WS-V00832 PIC X(8).
           05 WS-V00833 PIC X(8).
           05 WS-V00834 PIC X(8).
           05 WS-V00835 PIC X(8).
           05 WS-V00836 PIC X(8).
           05 WS-V00837 PIC X(8).
           05 WS-V00838 PIC X(8).
           05 WS-V00839 PIC X(8).
           05 WS-V00840 PIC X(8).
           05 WS-V00841 PIC X(8).
           05 WS-V00842 PIC X(8).
           05 WS-V00843 PIC X(8).
           05 WS-V00844 PIC X(8).
           05 WS-V00845 PIC X(8).
           05 WS-V00846 PIC X(8).
           05 WS-V00847 PIC X(8).
           05 WS-V00848 PIC X(8).
           05 WS-V00849 PIC X(8).
           05 WS-V00850 PIC X(8).
           05 WS-V00851 PIC X(8).
           05 WS-V00852 PIC X(8).
           05 WS-V00853 PIC X(8).
           05 WS-V00854 PIC X(8).
           05 WS-V00855 PIC X(8).
           05 WS-V00856 PIC X(8).
           05 WS-V00857 PIC X(8).
           05 WS-V00858 PIC X(8).
           05 WS-V00859 PIC X(8).
           05 WS-V00860 PIC X(8).
           05 WS-V00861 PIC X(8).
           05 WS-V00862 PIC X(8).
           05 WS-V00863 PIC X(8).
           05 WS-V00864 PIC X(8).
           05 WS-V00865 PIC X(8).
           05 WS-V00866 PIC X(8).
           05 WS-V00867 PIC X(8).
           05 WS-V00868 PIC X(8).
           05 WS-V00869 PIC X(8).
           05 WS-V00870 PIC X(8).
           05 WS-V00871 PIC X(8).
           05 WS-V00872 PIC X(8).
           05 WS-V00873 PIC X(8).
           05 WS-V00874 PIC X(8).
           05 WS-V00875 PIC X(8).
           05 WS-V00876 PIC X(8).
           05 WS-V00877 PIC X(8).
           05 WS-V00878 PIC X(8).
           05 WS-V00879 PIC X(8).
           05 WS-V00880 PIC X(8).
           05 WS-V00881 PIC X(8).
           05 WS-V00882 PIC X(8).
           05 WS-V00883 PIC X(8).
           05 WS-V00884 PIC X(8).
           05 WS-V00885 PIC X(8).
           05 WS-V00886 PIC X(8).
           05 WS-V00887 PIC X(8).
           05 WS-V00888 PIC X(8).
           05 WS-V00889 PIC X(8).
           05 WS-V00890 PIC X(8).
           05 WS-V00891 PIC X(8).
           05 WS-V00892 PIC X(8).
           05 WS-V00893 PIC X(8).
           05 WS-V00894 PIC X(8).
           05 WS-V00895 PIC X(8).
           05 WS-V00896 PIC X(8).
           05 WS-V00897 PIC X(8).
           05 WS-V00898 PIC X(8).
           05 WS-V00899 PIC X(8).
           05 WS-V00900 PIC X(8).
           05 WS-V00901 PIC X(8).
           05 WS-V00902 PIC X(8).
           05 WS-V00903 PIC X(8).
           05 WS-V00904 PIC X(8).
           05 WS-V00905 PIC X(8).
           05 WS-V00906 PIC X(8).
           05 WS-V00907 PIC X(8).
           05 WS-V00908 PIC X(8).
           05 WS-V00909 PIC X(8).
           05 WS-V00910 PIC X(8).
           05 WS-V00911 PIC X(8).
           05 WS-V00912 PIC X(8).
           05 WS-V00913 PIC X(8).
           05 WS-V00914 PIC X(8).
           05 WS-V00915 PIC X(8).
           05 WS-V00916 PIC X(8).
           05 WS-V00917 PIC X(8).
           05 WS-V00918 PIC X(8).
           05 WS-V00919 PIC X(8).
           05 WS-V00920 PIC X(8).
           05 WS-V00921 PIC X(8).
           05 WS-V00922 PIC X(8).
           05 WS-V00923 PIC X(8).
           05 WS-V00924 PIC X(8).
           05 WS-V00925 PIC X(8).
           05 WS-V00926 PIC X(8).
           05 WS-V00927 PIC X(8).
           05 WS-V00928 PIC X(8).
           05 WS-V00929 PIC X(8).
           05 WS-V00930 PIC X(8).
           05 WS-V00931 PIC X(8).
           05 WS-V00932 PIC X(8).
           05 WS-V00933 PIC X(8).
           05 WS-V00934 PIC X(8).
           05 WS-V00935 PIC X(8).
           05 WS-V00936 PIC X(8).
           05 WS-V00937 PIC X(8).
           05 WS-V00938 PIC X(8).
           05 WS-V00939 PIC X(8).
           05 WS-V00940 PIC X(8).
           05 WS-V00941 PIC X(8).
           05 WS-V00942 PIC X(8).
           05 WS-V00943 PIC X(8).
           05 WS-V00944 PIC X(8).
           05 WS-V00945 PIC X(8).
           05 WS-V00946 PIC X(8).
           05 WS-V00947 PIC X(8).
           05 WS-V00948 PIC X(8).
           05 WS-V00949 PIC X(8).
           05 WS-V00950 PIC X(8).
           05 WS-V00951 PIC X(8).
           05 WS-V00952 PIC X(8).
           05 WS-V00953 PIC X(8).
           05 WS-V00954 PIC X(8).
           05 WS-V00955 PIC X(8).
           05 WS-V00956 PIC X(8).
           05 WS-V00957 PIC X(8).
           05 WS-V00958 PIC X(8).
           05 WS-V00959 PIC X(8).
           05 WS-V00960 PIC X(8).
           05 WS-V00961 PIC X(8).
           05 WS-V00962 PIC X(8).
           05 WS-V00963 PIC X(8).
           05 WS-V00964 PIC X(8).
           05 WS-V00965 PIC X(8).
           05 WS-V00966 PIC X(8).
           05 WS-V00967 PIC X(8).
           05 WS-V00968 PIC X(8).
           05 WS-V00969 PIC X(8).
           05 WS-V00970 PIC X(8).
           05 WS-V00971 PIC X(8).
           05 WS-V00972 PIC X(8).
           05 WS-V00973 PIC X(8).
           05 WS-V00974 PIC X(8).
           05 WS-V00975 PIC X(8).
           05 WS-V00976 PIC X(8).
           05 WS-V00977 PIC X(8).
           05 WS-V00978 PIC X(8).
           05 WS-V00979 PIC X(8).
           05 WS-V00980 PIC X(8).
           05 WS-V00981 PIC X(8).
           05 WS-V00982 PIC X(8).
           05 WS-V00983 PIC X(8).
           05 WS-V00984 PIC X(8).
           05 WS-V00985 PIC X(8).
           05 WS-V00986 PIC X(8).
           05 WS-V00987 PIC X(8).
           05 WS-V00988 PIC X(8).
           05 WS-V00989 PIC X(8).
           05 WS-V00990 PIC X(8).
           05 WS-V00991 PIC X(8).
           05 WS-V00992 PIC X(8).
           05 WS-V00993 PIC X(8).
           05 WS-V00994 PIC X(8).
           05 WS-V00995 PIC X(8).
           05 WS-V00996 PIC X(8).
           05 WS-V00997 PIC X(8).
           05 WS-V00998 PIC X(8).
           05 WS-V00999 PIC X(8).
           05 WS-V01000 PIC X(8).
           05 WS-V01001 PIC X(8).
           05 WS-V01002 PIC X(8).
           05 WS-V01003 PIC X(8).
           05 WS-V01004 PIC X(8).
           05 WS-V01005 PIC X(8).
           05 WS-V01006 PIC X(8).
           05 WS-V01007 PIC X(8).
           05 WS-V01008 PIC X(8).
           05 WS-V01009 PIC X(8).
           05 WS-V01010 PIC X(8).
           05 WS-V01011 PIC X(8).
           05 WS-V01012 PIC X(8).
           05 WS-V01013 PIC X(8).
           05 WS-V01014 PIC X(8).
           05 WS-V01015 PIC X(8).
           05 WS-V01016 PIC X(8).
           05 WS-V01017 PIC X(8).
           05 WS-V01018 PIC X(8).
           05 WS-V01019 PIC X(8).
           05 WS-V01020 PIC X(8).
           05 WS-V01021 PIC X(8).
           05 WS-V01022 PIC X(8).
           05 WS-V01023 PIC X(8).
           05 WS-V01024 PIC X(8).
           05 WS-V01025 PIC X(8).
           05 WS-V01026 PIC X(8).
           05 WS-V01027 PIC X(8).
           05 WS-V01028 PIC X(8).
           05 WS-V01029 PIC X(8).
           05 WS-V01030 PIC X(8).
           05 WS-V01031 PIC X(8).
           05 WS-V01032 PIC X(8).
           05 WS-V01033 PIC X(8).
           05 WS-V01034 PIC X(8).
           05 WS-V01035 PIC X(8).
           05 WS-V01036 PIC X(8).
           05 WS-V01037 PIC X(8).
           05 WS-V01038 PIC X(8).
           05 WS-V01039 PIC X(8).
           05 WS-V01040 PIC X(8).
           05 WS-V01041 PIC X(8).
           05 WS-V01042 PIC X(8).
           05 WS-V01043 PIC X(8).
           05 WS-V01044 PIC X(8).
           05 WS-V01045 PIC X(8).
           05 WS-V01046 PIC X(8).
           05 WS-V01047 PIC X(8).
           05 WS-V01048 PIC X(8).
           05 WS-V01049 PIC X(8).
           05 WS-V01050 PIC X(8).
           05 WS-V01051 PIC X(8).
           05 WS-V01052 PIC X(8).
           05 WS-V01053 PIC X(8).
           05 WS-V01054 PIC X(8).
           05 WS-V01055 PIC X(8).
           05 WS-V01056 PIC X(8).
           05 WS-V01057 PIC X(8).
           05 WS-V01058 PIC X(8).
           05 WS-V01059 PIC X(8).
           05 WS-V01060 PIC X(8).
           05 WS-V01061 PIC X(8).
           05 WS-V01062 PIC X(8).
           05 WS-V01063 PIC X(8).
           05 WS-V01064 PIC X(8).
           05 WS-V01065 PIC X(8).
           05 WS-V01066 PIC X(8).
           05 WS-V01067 PIC X(8).
           05 WS-V01068 PIC X(8).
           05 WS-V01069 PIC X(8).
           05 WS-V01070 PIC X(8).
           05 WS-V01071 PIC X(8).
           05 WS-V01072 PIC X(8).
           05 WS-V01073 PIC X(8).
           05 WS-V01074 PIC X(8).
           05 WS-V01075 PIC X(8).
           05 WS-V01076 PIC X(8).
           05 WS-V01077 PIC X(8).
           05 WS-V01078 PIC X(8).
           05 WS-V01079 PIC X(8).
           05 WS-V01080 PIC X(8).
           05 WS-V01081 PIC X(8).
           05 WS-V01082 PIC X(8).
           05 WS-V01083 PIC X(8).
           05 WS-V01084 PIC X(8).
           05 WS-V01085 PIC X(8).
           05 WS-V01086 PIC X(8).
           05 WS-V01087 PIC X(8).
           05 WS-V01088 PIC X(8).
           05 WS-V01089 PIC X(8).
           05 WS-V01090 PIC X(8).
           05 WS-V01091 PIC X(8).
           05 WS-V01092 PIC X(8).
           05 WS-V01093 PIC X(8).
           05 WS-V01094 PIC X(8).
           05 WS-V01095 PIC X(8).
           05 WS-V01096 PIC X(8).
           05 WS-V01097 PIC X(8).
           05 WS-V01098 PIC X(8).
           05 WS-V01099 PIC X(8).
           05 WS-V01100 PIC X(8).
           05 WS-V01101 PIC X(8).
           05 WS-V01102 PIC X(8).
           05 WS-V01103 PIC X(8).
           05 WS-V01104 PIC X(8).
           05 WS-V01105 PIC X(8).
           05 WS-V01106 PIC X(8).
           05 WS-V01107 PIC X(8).
           05 WS-V01108 PIC X(8).
           05 WS-V01109 PIC X(8).
           05 WS-V01110 PIC X(8).
           05 WS-V01111 PIC X(8).
           05 WS-V01112 PIC X(8).
           05 WS-V01113 PIC X(8).
           05 WS-V01114 PIC X(8).
           05 WS-V01115 PIC X(8).
           05 WS-V01116 PIC X(8).
           05 WS-V01117 PIC X(8).
           05 WS-V01118 PIC X(8).
           05 WS-V01119 PIC X(8).
           05 WS-V01120 PIC X(8).
           05 WS-V01121 PIC X(8).
           05 WS-V01122 PIC X(8).
           05 WS-V01123 PIC X(8).
           05 WS-V01124 PIC X(8).
           05 WS-V01125 PIC X(8).
           05 WS-V01126 PIC X(8).
           05 WS-V01127 PIC X(8).
           05 WS-V01128 PIC X(8).
           05 WS-V01129 PIC X(8).
           05 WS-V01130 PIC X(8).
           05 WS-V01131 PIC X(8).
           05 WS-V01132 PIC X(8).
           05 WS-V01133 PIC X(8).
           05 WS-V01134 PIC X(8).
           05 WS-V01135 PIC X(8).
           05 WS-V01136 PIC X(8).
           05 WS-V01137 PIC X(8).
           05 WS-V01138 PIC X(8).
           05 WS-V01139 PIC X(8).
           05 WS-V01140 PIC X(8).
           05 WS-V01141 PIC X(8).
           05 WS-V01142 PIC X(8).
           05 WS-V01143 PIC X(8).
           05 WS-V01144 PIC X(8).
           05 WS-V01145 PIC X(8).
           05 WS-V01146 PIC X(8).
           05 WS-V01147 PIC X(8).
           05 WS-V01148 PIC X(8).
           05 WS-V01149 PIC X(8).
           05 WS-V01150 PIC X(8).
           05 WS-V01151 PIC X(8).
           05 WS-V01152 PIC X(8).
           05 WS-V01153 PIC X(8).
           05 WS-V01154 PIC X(8).
           05 WS-V01155 PIC X(8).
           05 WS-V01156 PIC X(8).
           05 WS-V01157 PIC X(8).
           05 WS-V01158 PIC X(8).
           05 WS-V01159 PIC X(8).
           05 WS-V01160 PIC X(8).
           05 WS-V01161 PIC X(8).
           05 WS-V01162 PIC X(8).
           05 WS-V01163 PIC X(8).
           05 WS-V01164 PIC X(8).
           05 WS-V01165 PIC X(8).
           05 WS-V01166 PIC X(8).
           05 WS-V01167 PIC X(8).
           05 WS-V01168 PIC X(8).
           05 WS-V01169 PIC X(8).
           05 WS-V01170 PIC X(8).
           05 WS-V01171 PIC X(8).
           05 WS-V01172 PIC X(8).
           05 WS-V01173 PIC X(8).
           05 WS-V01174 PIC X(8).
           05 WS-V01175 PIC X(8).
           05 WS-V01176 PIC X(8).
           05 WS-V01177 PIC X(8).
           05 WS-V01178 PIC X(8).
           05 WS-V01179 PIC X(8).
           05 WS-V01180 PIC X(8).
           05 WS-V01181 PIC X(8).
           05 WS-V01182 PIC X(8).
           05 WS-V01183 PIC X(8).
           05 WS-V01184 PIC X(8).
           05 WS-V01185 PIC X(8).
           05 WS-V01186 PIC X(8).
           05 WS-V01187 PIC X(8).
           05 WS-V01188 PIC X(8).
           05 WS-V01189 PIC X(8).
           05 WS-V01190 PIC X(8).
           05 WS-V01191 PIC X(8).
           05 WS-V01192 PIC X(8).
           05 WS-V01193 PIC X(8).
           05 WS-V01194 PIC X(8).
           05 WS-V01195 PIC X(8).
           05 WS-V01196 PIC X(8).
           05 WS-V01197 PIC X(8).
           05 WS-V01198 PIC X(8).
           05 WS-V01199 PIC X(8).
           05 WS-V01200 PIC X(8).
           05 WS-V01201 PIC X(8).
           05 WS-V01202 PIC X(8).
           05 WS-V01203 PIC X(8).
           05 WS-V01204 PIC X(8).
           05 WS-V01205 PIC X(8).
           05 WS-V01206 PIC X(8).
           05 WS-V01207 PIC X(8).
           05 WS-V01208 PIC X(8).
           05 WS-V01209 PIC X(8).
           05 WS-V01210 PIC X(8).
           05 WS-V01211 PIC X(8).
           05 WS-V01212 PIC X(8).
           05 WS-V01213 PIC X(8).
           05 WS-V01214 PIC X(8).
           05 WS-V01215 PIC X(8).
           05 WS-V01216 PIC X(8).
           05 WS-V01217 PIC X(8).
           05 WS-V01218 PIC X(8).
           05 WS-V01219 PIC X(8).
           05 WS-V01220 PIC X(8).
           05 WS-V01221 PIC X(8).
           05 WS-V01222 PIC X(8).
           05 WS-V01223 PIC X(8).
           05 WS-V01224 PIC X(8).
           05 WS-V01225 PIC X(8).
           05 WS-V01226 PIC X(8).
           05 WS-V01227 PIC X(8).
           05 WS-V01228 PIC X(8).
           05 WS-V01229 PIC X(8).
           05 WS-V01230 PIC X(8).
           05 WS-V01231 PIC X(8).
           05 WS-V01232 PIC X(8).
           05 WS-V01233 PIC X(8).
           05 WS-V01234 PIC X(8).
           05 WS-V01235 PIC X(8).
           05 WS-V01236 PIC X(8).
           05 WS-V01237 PIC X(8).
           05 WS-V01238 PIC X(8).
           05 WS-V01239 PIC X(8).
           05 WS-V01240 PIC X(8).
           05 WS-V01241 PIC X(8).
           05 WS-V01242 PIC X(8).
           05 WS-V01243 PIC X(8).
           05 WS-V01244 PIC X(8).
           05 WS-V01245 PIC X(8).
           05 WS-V01246 PIC X(8).
           05 WS-V01247 PIC X(8).
           05 WS-V01248 PIC X(8).
           05 WS-V01249 PIC X(8).
           05 WS-V01250 PIC X(8).
           05 WS-V01251 PIC X(8).
           05 WS-V01252 PIC X(8).
           05 WS-V01253 PIC X(8).
           05 WS-V01254 PIC X(8).
           05 WS-V01255 PIC X(8).
           05 WS-V01256 PIC X(8).
           05 WS-V01257 PIC X(8).
           05 WS-V01258 PIC X(8).
           05 WS-V01259 PIC X(8).
           05 WS-V01260 PIC X(8).
           05 WS-V01261 PIC X(8).
           05 WS-V01262 PIC X(8).
           05 WS-V01263 PIC X(8).
           05 WS-V01264 PIC X(8).
           05 WS-V01265 PIC X(8).
           05 WS-V01266 PIC X(8).
           05 WS-V01267 PIC X(8).
           05 WS-V01268 PIC X(8).
           05 WS-V01269 PIC X(8).
           05 WS-V01270 PIC X(8).
           05 WS-V01271 PIC X(8).
           05 WS-V01272 PIC X(8).
           05 WS-V01273 PIC X(8).
           05 WS-V01274 PIC X(8).
           05 WS-V01275 PIC X(8).
           05 WS-V01276 PIC X(8).
           05 WS-V01277 PIC X(8).
           05 WS-V01278 PIC X(8).
           05 WS-V01279 PIC X(8).
           05 WS-V01280 PIC X(8).
           05 WS-V01281 PIC X(8).
           05 WS-V01282 PIC X(8).
           05 WS-V01283 PIC X(8).
           05 WS-V01284 PIC X(8).
           05 WS-V01285 PIC X(8).
           05 WS-V01286 PIC X(8).
           05 WS-V01287 PIC X(8).
           05 WS-V01288 PIC X(8).
           05 WS-V01289 PIC X(8).
           05 WS-V01290 PIC X(8).
           05 WS-V01291 PIC X(8).
           05 WS-V01292 PIC X(8).
           05 WS-V01293 PIC X(8).
           05 WS-V01294 PIC X(8).
           05 WS-V01295 PIC X(8).
           05 WS-V01296 PIC X(8).
           05 WS-V01297 PIC X(8).
           05 WS-V01298 PIC X(8).
           05 WS-V01299 PIC X(8).
           05 WS-V01300 PIC X(8).
           05 WS-V01301 PIC X(8).
           05 WS-V01302 PIC X(8).
           05 WS-V01303 PIC X(8).
           05 WS-V01304 PIC X(8).
           05 WS-V01305 PIC X(8).
           05 WS-V01306 PIC X(8).
           05 WS-V01307 PIC X(8).
           05 WS-V01308 PIC X(8).
           05 WS-V01309 PIC X(8).
           05 WS-V01310 PIC X(8).
           05 WS-V01311 PIC X(8).
           05 WS-V01312 PIC X(8).
           05 WS-V01313 PIC X(8).
           05 WS-V01314 PIC X(8).
           05 WS-V01315 PIC X(8).
           05 WS-V01316 PIC X(8).
           05 WS-V01317 PIC X(8).
           05 WS-V01318 PIC X(8).
           05 WS-V01319 PIC X(8).
           05 WS-V01320 PIC X(8).
           05 WS-V01321 PIC X(8).
           05 WS-V01322 PIC X(8).
           05 WS-V01323 PIC X(8).
           05 WS-V01324 PIC X(8).
           05 WS-V01325 PIC X(8).
           05 WS-V01326 PIC X(8).
           05 WS-V01327 PIC X(8).
           05 WS-V01328 PIC X(8).
           05 WS-V01329 PIC X(8).
           05 WS-V01330 PIC X(8).
           05 WS-V01331 PIC X(8).
           05 WS-V01332 PIC X(8).
           05 WS-V01333 PIC X(8).
           05 WS-V01334 PIC X(8).
           05 WS-V01335 PIC X(8).
           05 WS-V01336 PIC X(8).
           05 WS-V01337 PIC X(8).
           05 WS-V01338 PIC X(8).
           05 WS-V01339 PIC X(8).
           05 WS-V01340 PIC X(8).
           05 WS-V01341 PIC X(8).
           05 WS-V01342 PIC X(8).
           05 WS-V01343 PIC X(8).
           05 WS-V01344 PIC X(8).
           05 WS-V01345 PIC X(8).
           05 WS-V01346 PIC X(8).
           05 WS-V01347 PIC X(8).
           05 WS-V01348 PIC X(8).
           05 WS-V01349 PIC X(8).
           05 WS-V01350 PIC X(8).
           05 WS-V01351 PIC X(8).
           05 WS-V01352 PIC X(8).
           05 WS-V01353 PIC X(8).
           05 WS-V01354 PIC X(8).
           05 WS-V01355 PIC X(8).
           05 WS-V01356 PIC X(8).
           05 WS-V01357 PIC X(8).
           05 WS-V01358 PIC X(8).
           05 WS-V01359 PIC X(8).
           05 WS-V01360 PIC X(8).
           05 WS-V01361 PIC X(8).
           05 WS-V01362 PIC X(8).
           05 WS-V01363 PIC X(8).
           05 WS-V01364 PIC X(8).
           05 WS-V01365 PIC X(8).
           05 WS-V01366 PIC X(8).
           05 WS-V01367 PIC X(8).
           05 WS-V01368 PIC X(8).
           05 WS-V01369 PIC X(8).
           05 WS-V01370 PIC X(8).
           05 WS-V01371 PIC X(8).
           05 WS-V01372 PIC X(8).
           05 WS-V01373 PIC X(8).
           05 WS-V01374 PIC X(8).
           05 WS-V01375 PIC X(8).
           05 WS-V01376 PIC X(8).
           05 WS-V01377 PIC X(8).
           05 WS-V01378 PIC X(8).
           05 WS-V01379 PIC X(8).
           05 WS-V01380 PIC X(8).
           05 WS-V01381 PIC X(8).
           05 WS-V01382 PIC X(8).
           05 WS-V01383 PIC X(8).
           05 WS-V01384 PIC X(8).
           05 WS-V01385 PIC X(8).
           05 WS-V01386 PIC X(8).
           05 WS-V01387 PIC X(8).
           05 WS-V01388 PIC X(8).
           05 WS-V01389 PIC X(8).
           05 WS-V01390 PIC X(8).
           05 WS-V01391 PIC X(8).
           05 WS-V01392 PIC X(8).
           05 WS-V01393 PIC X(8).
           05 WS-V01394 PIC X(8).
           05 WS-V01395 PIC X(8).
           05 WS-V01396 PIC X(8).
           05 WS-V01397 PIC X(8).
           05 WS-V01398 PIC X(8).
           05 WS-V01399 PIC X(8).
           05 WS-V01400 PIC X(8).
           05 WS-V01401 PIC X(8).
           05 WS-V01402 PIC X(8).
           05 WS-V01403 PIC X(8).
           05 WS-V01404 PIC X(8).
           05 WS-V01405 PIC X(8).
           05 WS-V01406 PIC X(8).
           05 WS-V01407 PIC X(8).
           05 WS-V01408 PIC X(8).
           05 WS-V01409 PIC X(8).
           05 WS-V01410 PIC X(8).
           05 WS-V01411 PIC X(8).
           05 WS-V01412 PIC X(8).
           05 WS-V01413 PIC X(8).
           05 WS-V01414 PIC X(8).
           05 WS-V01415 PIC X(8).
           05 WS-V01416 PIC X(8).
           05 WS-V01417 PIC X(8).
           05 WS-V01418 PIC X(8).
           05 WS-V01419 PIC X(8).
           05 WS-V01420 PIC X(8).
           05 WS-V01421 PIC X(8).
           05 WS-V01422 PIC X(8).
           05 WS-V01423 PIC X(8).
           05 WS-V01424 PIC X(8).
           05 WS-V01425 PIC X(8).
           05 WS-V01426 PIC X(8).
           05 WS-V01427 PIC X(8).
           05 WS-V01428 PIC X(8).
           05 WS-V01429 PIC X(8).
           05 WS-V01430 PIC X(8).
           05 WS-V01431 PIC X(8).
           05 WS-V01432 PIC X(8).
           05 WS-V01433 PIC X(8).
           05 WS-V01434 PIC X(8).
           05 WS-V01435 PIC X(8).
           05 WS-V01436 PIC X(8).
           05 WS-V01437 PIC X(8).
           05 WS-V01438 PIC X(8).
           05 WS-V01439 PIC X(8).
           05 WS-V01440 PIC X(8).
           05 WS-V01441 PIC X(8).
           05 WS-V01442 PIC X(8).
           05 WS-V01443 PIC X(8).
           05 WS-V01444 PIC X(8).
           05 WS-V01445 PIC X(8).
           05 WS-V01446 PIC X(8).
           05 WS-V01447 PIC X(8).
           05 WS-V01448 PIC X(8).
           05 WS-V01449 PIC X(8).
           05 WS-V01450 PIC X(8).
           05 WS-V01451 PIC X(8).
           05 WS-V01452 PIC X(8).
           05 WS-V01453 PIC X(8).
           05 WS-V01454 PIC X(8).
           05 WS-V01455 PIC X(8).
           05 WS-V01456 PIC X(8).
           05 WS-V01457 PIC X(8).
           05 WS-V01458 PIC X(8).
           05 WS-V01459 PIC X(8).
           05 WS-V01460 PIC X(8).
           05 WS-V01461 PIC X(8).
           05 WS-V01462 PIC X(8).
           05 WS-V01463 PIC X(8).
           05 WS-V01464 PIC X(8).
           05 WS-V01465 PIC X(8).
           05 WS-V01466 PIC X(8).
           05 WS-V01467 PIC X(8).
           05 WS-V01468 PIC X(8).
           05 WS-V01469 PIC X(8).
           05 WS-V01470 PIC X(8).
           05 WS-V01471 PIC X(8).
           05 WS-V01472 PIC X(8).
           05 WS-V01473 PIC X(8).
           05 WS-V01474 PIC X(8).
           05 WS-V01475 PIC X(8).
           05 WS-V01476 PIC X(8).
           05 WS-V01477 PIC X(8).
           05 WS-V01478 PIC X(8).
           05 WS-V01479 PIC X(8).
           05 WS-V01480 PIC X(8).
           05 WS-V01481 PIC X(8).
           05 WS-V01482 PIC X(8).
           05 WS-V01483 PIC X(8).
           05 WS-V01484 PIC X(8).
           05 WS-V01485 PIC X(8).
           05 WS-V01486 PIC X(8).
           05 WS-V01487 PIC X(8).
           05 WS-V01488 PIC X(8).
           05 WS-V01489 PIC X(8).
           05 WS-V01490 PIC X(8).
           05 WS-V01491 PIC X(8).
           05 WS-V01492 PIC X(8).
           05 WS-V01493 PIC X(8).
           05 WS-V01494 PIC X(8).
           05 WS-V01495 PIC X(8).
           05 WS-V01496 PIC X(8).
           05 WS-V01497 PIC X(8).
           05 WS-V01498 PIC X(8).
           05 WS-V01499 PIC X(8).
           05 WS-V01500 PIC X(8).
           05 WS-V01501 PIC X(8).
           05 WS-V01502 PIC X(8).
           05 WS-V01503 PIC X(8).
           05 WS-V01504 PIC X(8).
           05 WS-V01505 PIC X(8).
           05 WS-V01506 PIC X(8).
           05 WS-V01507 PIC X(8).
           05 WS-V01508 PIC X(8).
           05 WS-V01509 PIC X(8).
           05 WS-V01510 PIC X(8).
           05 WS-V01511 PIC X(8).
           05 WS-V01512 PIC X(8).
           05 WS-V01513 PIC X(8).
           05 WS-V01514 PIC X(8).
           05 WS-V01515 PIC X(8).
           05 WS-V01516 PIC X(8).
           05 WS-V01517 PIC X(8).
           05 WS-V01518 PIC X(8).
           05 WS-V01519 PIC X(8).
           05 WS-V01520 PIC X(8).
           05 WS-V01521 PIC X(8).
           05 WS-V01522 PIC X(8).
           05 WS-V01523 PIC X(8).
           05 WS-V01524 PIC X(8).
           05 WS-V01525 PIC X(8).
           05 WS-V01526 PIC X(8).
           05 WS-V01527 PIC X(8).
           05 WS-V01528 PIC X(8).
           05 WS-V01529 PIC X(8).
           05 WS-V01530 PIC X(8).
           05 WS-V01531 PIC X(8).
           05 WS-V01532 PIC X(8).
           05 WS-V01533 PIC X(8).
           05 WS-V01534 PIC X(8).
           05 WS-V01535 PIC X(8).
           05 WS-V01536 PIC X(8).
           05 WS-V01537 PIC X(8).
           05 WS-V01538 PIC X(8).
           05 WS-V01539 PIC X(8).
           05 WS-V01540 PIC X(8).
           05 WS-V01541 PIC X(8).
           05 WS-V01542 PIC X(8).
           05 WS-V01543 PIC X(8).
           05 WS-V01544 PIC X(8).
           05 WS-V01545 PIC X(8).
           05 WS-V01546 PIC X(8).
           05 WS-V01547 PIC X(8).
           05 WS-V01548 PIC X(8).
           05 WS-V01549 PIC X(8).
           05 WS-V01550 PIC X(8).
           05 WS-V01551 PIC X(8).
           05 WS-V01552 PIC X(8).
           05 WS-V01553 PIC X(8).
           05 WS-V01554 PIC X(8).
           05 WS-V01555 PIC X(8).
           05 WS-V01556 PIC X(8).
           05 WS-V01557 PIC X(8).
           05 WS-V01558 PIC X(8).
           05 WS-V01559 PIC X(8).
           05 WS-V01560 PIC X(8).
           05 WS-V01561 PIC X(8).
           05 WS-V01562 PIC X(8).
           05 WS-V01563 PIC X(8).
           05 WS-V01564 PIC X(8).
           05 WS-V01565 PIC X(8).
           05 WS-V01566 PIC X(8).
           05 WS-V01567 PIC X(8).
           05 WS-V01568 PIC X(8).
           05 WS-V01569 PIC X(8).
           05 WS-V01570 PIC X(8).
           05 WS-V01571 PIC X(8).
           05 WS-V01572 PIC X(8).
           05 WS-V01573 PIC X(8).
           05 WS-V01574 PIC X(8).
           05 WS-V01575 PIC X(8).
           05 WS-V01576 PIC X(8).
           05 WS-V01577 PIC X(8).
           05 WS-V01578 PIC X(8).
           05 WS-V01579 PIC X(8).
           05 WS-V01580 PIC X(8).
           05 WS-V01581 PIC X(8).
           05 WS-V01582 PIC X(8).
           05 WS-V01583 PIC X(8).
           05 WS-V01584 PIC X(8).
           05 WS-V01585 PIC X(8).
           05 WS-V01586 PIC X(8).
           05 WS-V01587 PIC X(8).
           05 WS-V01588 PIC X(8).
           05 WS-V01589 PIC X(8).
           05 WS-V01590 PIC X(8).
           05 WS-V01591 PIC X(8).
           05 WS-V01592 PIC X(8).
           05 WS-V01593 PIC X(8).
           05 WS-V01594 PIC X(8).
           05 WS-V01595 PIC X(8).
           05 WS-V01596 PIC X(8).
           05 WS-V01597 PIC X(8).
           05 WS-V01598 PIC X(8).
           05 WS-V01599 PIC X(8).
           05 WS-V01600 PIC X(8).
           05 WS-V01601 PIC X(8).
           05 WS-V01602 PIC X(8).
           05 WS-V01603 PIC X(8).
           05 WS-V01604 PIC X(8).
           05 WS-V01605 PIC X(8).
           05 WS-V01606 PIC X(8).
           05 WS-V01607 PIC X(8).
           05 WS-V01608 PIC X(8).
           05 WS-V01609 PIC X(8).
           05 WS-V01610 PIC X(8).
           05 WS-V01611 PIC X(8).
           05 WS-V01612 PIC X(8).
           05 WS-V01613 PIC X(8).
           05 WS-V01614 PIC X(8).
           05 WS-V01615 PIC X(8).
           05 WS-V01616 PIC X(8).
           05 WS-V01617 PIC X(8).
           05 WS-V01618 PIC X(8).
           05 WS-V01619 PIC X(8).
           05 WS-V01620 PIC X(8).
           05 WS-V01621 PIC X(8).
           05 WS-V01622 PIC X(8).
           05 WS-V01623 PIC X(8).
           05 WS-V01624 PIC X(8).
           05 WS-V01625 PIC X(8).
           05 WS-V01626 PIC X(8).
           05 WS-V01627 PIC X(8).
           05 WS-V01628 PIC X(8).
           05 WS-V01629 PIC X(8).
           05 WS-V01630 PIC X(8).
           05 WS-V01631 PIC X(8).
           05 WS-V01632 PIC X(8).
           05 WS-V01633 PIC X(8).
           05 WS-V01634 PIC X(8).
           05 WS-V01635 PIC X(8).
           05 WS-V01636 PIC X(8).
           05 WS-V01637 PIC X(8).
           05 WS-V01638 PIC X(8).
           05 WS-V01639 PIC X(8).
           05 WS-V01640 PIC X(8).
           05 WS-V01641 PIC X(8).
           05 WS-V01642 PIC X(8).
           05 WS-V01643 PIC X(8).
           05 WS-V01644 PIC X(8).
           05 WS-V01645 PIC X(8).
           05 WS-V01646 PIC X(8).
           05 WS-V01647 PIC X(8).
           05 WS-V01648 PIC X(8).
           05 WS-V01649 PIC X(8).
           05 WS-V01650 PIC X(8).
           05 WS-V01651 PIC X(8).
           05 WS-V01652 PIC X(8).
           05 WS-V01653 PIC X(8).
           05 WS-V01654 PIC X(8).
           05 WS-V01655 PIC X(8).
           05 WS-V01656 PIC X(8).
           05 WS-V01657 PIC X(8).
           05 WS-V01658 PIC X(8).
           05 WS-V01659 PIC X(8).
           05 WS-V01660 PIC X(8).
           05 WS-V01661 PIC X(8).
           05 WS-V01662 PIC X(8).
           05 WS-V01663 PIC X(8).
           05 WS-V01664 PIC X(8).
           05 WS-V01665 PIC X(8).
           05 WS-V01666 PIC X(8).
           05 WS-V01667 PIC X(8).
           05 WS-V01668 PIC X(8).
           05 WS-V01669 PIC X(8).
           05 WS-V01670 PIC X(8).
           05 WS-V01671 PIC X(8).
           05 WS-V01672 PIC X(8).
           05 WS-V01673 PIC X(8).
           05 WS-V01674 PIC X(8).
           05 WS-V01675 PIC X(8).
           05 WS-V01676 PIC X(8).
           05 WS-V01677 PIC X(8).
           05 WS-V01678 PIC X(8).
           05 WS-V01679 PIC X(8).
           05 WS-V01680 PIC X(8).
           05 WS-V01681 PIC X(8).
           05 WS-V01682 PIC X(8).
           05 WS-V01683 PIC X(8).
           05 WS-V01684 PIC X(8).
           05 WS-V01685 PIC X(8).
           05 WS-V01686 PIC X(8).
           05 WS-V01687 PIC X(8).
           05 WS-V01688 PIC X(8).
           05 WS-V01689 PIC X(8).
           05 WS-V01690 PIC X(8).
           05 WS-V01691 PIC X(8).
           05 WS-V01692 PIC X(8).
           05 WS-V01693 PIC X(8).
           05 WS-V01694 PIC X(8).
           05 WS-V01695 PIC X(8).
           05 WS-V01696 PIC X(8).
           05 WS-V01697 PIC X(8).
           05 WS-V01698 PIC X(8).
           05 WS-V01699 PIC X(8).
           05 WS-V01700 PIC X(8).
           05 WS-V01701 PIC X(8).
           05 WS-V01702 PIC X(8).
           05 WS-V01703 PIC X(8).
           05 WS-V01704 PIC X(8).
           05 WS-V01705 PIC X(8).
           05 WS-V01706 PIC X(8).
           05 WS-V01707 PIC X(8).
           05 WS-V01708 PIC X(8).
           05 WS-V01709 PIC X(8).
           05 WS-V01710 PIC X(8).
           05 WS-V01711 PIC X(8).
           05 WS-V01712 PIC X(8).
           05 WS-V01713 PIC X(8).
           05 WS-V01714 PIC X(8).
           05 WS-V01715 PIC X(8).
           05 WS-V01716 PIC X(8).
           05 WS-V01717 PIC X(8).
           05 WS-V01718 PIC X(8).
           05 WS-V01719 PIC X(8).
           05 WS-V01720 PIC X(8).
           05 WS-V01721 PIC X(8).
           05 WS-V01722 PIC X(8).
           05 WS-V01723 PIC X(8).
           05 WS-V01724 PIC X(8).
           05 WS-V01725 PIC X(8).
           05 WS-V01726 PIC X(8).
           05 WS-V01727 PIC X(8).
           05 WS-V01728 PIC X(8).
           05 WS-V01729 PIC X(8).
           05 WS-V01730 PIC X(8).
           05 WS-V01731 PIC X(8).
           05 WS-V01732 PIC X(8).
           05 WS-V01733 PIC X(8).
           05 WS-V01734 PIC X(8).
           05 WS-V01735 PIC X(8).
           05 WS-V01736 PIC X(8).
           05 WS-V01737 PIC X(8).
           05 WS-V01738 PIC X(8).
           05 WS-V01739 PIC X(8).
           05 WS-V01740 PIC X(8).
           05 WS-V01741 PIC X(8).
           05 WS-V01742 PIC X(8).
           05 WS-V01743 PIC X(8).
           05 WS-V01744 PIC X(8).
           05 WS-V01745 PIC X(8).
           05 WS-V01746 PIC X(8).
           05 WS-V01747 PIC X(8).
           05 WS-V01748 PIC X(8).
           05 WS-V01749 PIC X(8).
           05 WS-V01750 PIC X(8).
           05 WS-V01751 PIC X(8).
           05 WS-V01752 PIC X(8).
           05 WS-V01753 PIC X(8).
           05 WS-V01754 PIC X(8).
           05 WS-V01755 PIC X(8).
           05 WS-V01756 PIC X(8).
           05 WS-V01757 PIC X(8).
           05 WS-V01758 PIC X(8).
           05 WS-V01759 PIC X(8).
           05 WS-V01760 PIC X(8).
           05 WS-V01761 PIC X(8).
           05 WS-V01762 PIC X(8).
           05 WS-V01763 PIC X(8).
           05 WS-V01764 PIC X(8).
           05 WS-V01765 PIC X(8).
           05 WS-V01766 PIC X(8).
           05 WS-V01767 PIC X(8).
           05 WS-V01768 PIC X(8).
           05 WS-V01769 PIC X(8).
           05 WS-V01770 PIC X(8).
           05 WS-V01771 PIC X(8).
           05 WS-V01772 PIC X(8).
           05 WS-V01773 PIC X(8).
           05 WS-V01774 PIC X(8).
           05 WS-V01775 PIC X(8).
           05 WS-V01776 PIC X(8).
           05 WS-V01777 PIC X(8).
           05 WS-V01778 PIC X(8).
           05 WS-V01779 PIC X(8).
           05 WS-V01780 PIC X(8).
           05 WS-V01781 PIC X(8).
           05 WS-V01782 PIC X(8).
           05 WS-V01783 PIC X(8).
           05 WS-V01784 PIC X(8).
           05 WS-V01785 PIC X(8).
           05 WS-V01786 PIC X(8).
           05 WS-V01787 PIC X(8).
           05 WS-V01788 PIC X(8).
           05 WS-V01789 PIC X(8).
           05 WS-V01790 PIC X(8).
           05 WS-V01791 PIC X(8).
           05 WS-V01792 PIC X(8).
           05 WS-V01793 PIC X(8).
           05 WS-V01794 PIC X(8).
           05 WS-V01795 PIC X(8).
           05 WS-V01796 PIC X(8).
           05 WS-V01797 PIC X(8).
           05 WS-V01798 PIC X(8).
           05 WS-V01799 PIC X(8).
           05 WS-V01800 PIC X(8).
           05 WS-V01801 PIC X(8).
           05 WS-V01802 PIC X(8).
           05 WS-V01803 PIC X(8).
           05 WS-V01804 PIC X(8).
           05 WS-V01805 PIC X(8).
           05 WS-V01806 PIC X(8).
           05 WS-V01807 PIC X(8).
           05 WS-V01808 PIC X(8).
           05 WS-V01809 PIC X(8).
           05 WS-V01810 PIC X(8).
           05 WS-V01811 PIC X(8).
           05 WS-V01812 PIC X(8).
           05 WS-V01813 PIC X(8).
           05 WS-V01814 PIC X(8).
           05 WS-V01815 PIC X(8).
           05 WS-V01816 PIC X(8).
           05 WS-V01817 PIC X(8).
           05 WS-V01818 PIC X(8).
           05 WS-V01819 PIC X(8).
           05 WS-V01820 PIC X(8).
           05 WS-V01821 PIC X(8).
           05 WS-V01822 PIC X(8).
           05 WS-V01823 PIC X(8).
           05 WS-V01824 PIC X(8).
           05 WS-V01825 PIC X(8).
           05 WS-V01826 PIC X(8).
           05 WS-V01827 PIC X(8).
           05 WS-V01828 PIC X(8).
           05 WS-V01829 PIC X(8).
           05 WS-V01830 PIC X(8).
           05 WS-V01831 PIC X(8).
           05 WS-V01832 PIC X(8).
           05 WS-V01833 PIC X(8).
           05 WS-V01834 PIC X(8).
           05 WS-V01835 PIC X(8).
           05 WS-V01836 PIC X(8).
           05 WS-V01837 PIC X(8).
           05 WS-V01838 PIC X(8).
           05 WS-V01839 PIC X(8).
           05 WS-V01840 PIC X(8).
           05 WS-V01841 PIC X(8).
           05 WS-V01842 PIC X(8).
           05 WS-V01843 PIC X(8).
           05 WS-V01844 PIC X(8).
           05 WS-V01845 PIC X(8).
           05 WS-V01846 PIC X(8).
           05 WS-V01847 PIC X(8).
           05 WS-V01848 PIC X(8).
           05 WS-V01849 PIC X(8).
           05 WS-V01850 PIC X(8).
           05 WS-V01851 PIC X(8).
           05 WS-V01852 PIC X(8).
           05 WS-V01853 PIC X(8).
           05 WS-V01854 PIC X(8).
           05 WS-V01855 PIC X(8).
           05 WS-V01856 PIC X(8).
           05 WS-V01857 PIC X(8).
           05 WS-V01858 PIC X(8).
           05 WS-V01859 PIC X(8).
           05 WS-V01860 PIC X(8).
           05 WS-V01861 PIC X(8).
           05 WS-V01862 PIC X(8).
           05 WS-V01863 PIC X(8).
           05 WS-V01864 PIC X(8).
           05 WS-V01865 PIC X(8).
           05 WS-V01866 PIC X(8).
           05 WS-V01867 PIC X(8).
           05 WS-V01868 PIC X(8).
           05 WS-V01869 PIC X(8).
           05 WS-V01870 PIC X(8).
           05 WS-V01871 PIC X(8).
           05 WS-V01872 PIC X(8).
           05 WS-V01873 PIC X(8).
           05 WS-V01874 PIC X(8).
           05 WS-V01875 PIC X(8).
           05 WS-V01876 PIC X(8).
           05 WS-V01877 PIC X(8).
           05 WS-V01878 PIC X(8).
           05 WS-V01879 PIC X(8).
           05 WS-V01880 PIC X(8).
           05 WS-V01881 PIC X(8).
           05 WS-V01882 PIC X(8).
           05 WS-V01883 PIC X(8).
           05 WS-V01884 PIC X(8).
           05 WS-V01885 PIC X(8).
           05 WS-V01886 PIC X(8).
           05 WS-V01887 PIC X(8).
           05 WS-V01888 PIC X(8).
           05 WS-V01889 PIC X(8).
           05 WS-V01890 PIC X(8).
           05 WS-V01891 PIC X(8).
           05 WS-V01892 PIC X(8).
           05 WS-V01893 PIC X(8).
           05 WS-V01894 PIC X(8).
           05 WS-V01895 PIC X(8).
           05 WS-V01896 PIC X(8).
           05 WS-V01897 PIC X(8).
           05 WS-V01898 PIC X(8).
           05 WS-V01899 PIC X(8).
           05 WS-V01900 PIC X(8).
           05 WS-V01901 PIC X(8).
           05 WS-V01902 PIC X(8).
           05 WS-V01903 PIC X(8).
           05 WS-V01904 PIC X(8).
           05 WS-V01905 PIC X(8).
           05 WS-V01906 PIC X(8).
           05 WS-V01907 PIC X(8).
           05 WS-V01908 PIC X(8).
           05 WS-V01909 PIC X(8).
           05 WS-V01910 PIC X(8).
           05 WS-V01911 PIC X(8).
           05 WS-V01912 PIC X(8).
           05 WS-V01913 PIC X(8).
           05 WS-V01914 PIC X(8).
           05 WS-V01915 PIC X(8).
           05 WS-V01916 PIC X(8).
           05 WS-V01917 PIC X(8).
           05 WS-V01918 PIC X(8).
           05 WS-V01919 PIC X(8).
           05 WS-V01920 PIC X(8).
           05 WS-V01921 PIC X(8).
           05 WS-V01922 PIC X(8).
           05 WS-V01923 PIC X(8).
           05 WS-V01924 PIC X(8).
           05 WS-V01925 PIC X(8).
           05 WS-V01926 PIC X(8).
           05 WS-V01927 PIC X(8).
           05 WS-V01928 PIC X(8).
           05 WS-V01929 PIC X(8).
           05 WS-V01930 PIC X(8).
           05 WS-V01931 PIC X(8).
           05 WS-V01932 PIC X(8).
           05 WS-V01933 PIC X(8).
           05 WS-V01934 PIC X(8).
           05 WS-V01935 PIC X(8).
           05 WS-V01936 PIC X(8).
           05 WS-V01937 PIC X(8).
           05 WS-V01938 PIC X(8).
           05 WS-V01939 PIC X(8).
           05 WS-V01940 PIC X(8).
           05 WS-V01941 PIC X(8).
           05 WS-V01942 PIC X(8).
           05 WS-V01943 PIC X(8).
           05 WS-V01944 PIC X(8).
           05 WS-V01945 PIC X(8).
           05 WS-V01946 PIC X(8).
           05 WS-V01947 PIC X(8).
           05 WS-V01948 PIC X(8).
           05 WS-V01949 PIC X(8).
           05 WS-V01950 PIC X(8).
           05 WS-V01951 PIC X(8).
           05 WS-V01952 PIC X(8).
           05 WS-V01953 PIC X(8).
           05 WS-V01954 PIC X(8).
           05 WS-V01955 PIC X(8).
           05 WS-V01956 PIC X(8).
           05 WS-V01957 PIC X(8).
           05 WS-V01958 PIC X(8).
           05 WS-V01959 PIC X(8).
           05 WS-V01960 PIC X(8).
           05 WS-V01961 PIC X(8).
           05 WS-V01962 PIC X(8).
           05 WS-V01963 PIC X(8).
           05 WS-V01964 PIC X(8).
           05 WS-V01965 PIC X(8).
           05 WS-V01966 PIC X(8).
           05 WS-V01967 PIC X(8).
           05 WS-V01968 PIC X(8).
           05 WS-V01969 PIC X(8).
           05 WS-V01970 PIC X(8).
           05 WS-V01971 PIC X(8).
           05 WS-V01972 PIC X(8).
           05 WS-V01973 PIC X(8).
           05 WS-V01974 PIC X(8).
           05 WS-V01975 PIC X(8).
           05 WS-V01976 PIC X(8).
           05 WS-V01977 PIC X(8).
           05 WS-V01978 PIC X(8).
           05 WS-V01979 PIC X(8).
           05 WS-V01980 PIC X(8).
           05 WS-V01981 PIC X(8).
           05 WS-V01982 PIC X(8).
           05 WS-V01983 PIC X(8).
           05 WS-V01984 PIC X(8).
           05 WS-V01985 PIC X(8).
           05 WS-V01986 PIC X(8).
           05 WS-V01987 PIC X(8).
           05 WS-V01988 PIC X(8).
           05 WS-V01989 PIC X(8).
           05 WS-V01990 PIC X(8).
           05 WS-V01991 PIC X(8).
           05 WS-V01992 PIC X(8).
           05 WS-V01993 PIC X(8).
           05 WS-V01994 PIC X(8).
           05 WS-V01995 PIC X(8).
           05 WS-V01996 PIC X(8).
           05 WS-V01997 PIC X(8).
           05 WS-V01998 PIC X(8).
           05 WS-V01999 PIC X(8).
       PROCEDURE DIVISION.
       
       P00000.
           MOVE WS-V00275 TO WS-V01165.
           IF WS-V01735 = WS-V01643
               MOVE 'X' TO WS-V01564
           END-IF.
           IF WS-V00129 = WS-V00522
               MOVE 'X' TO WS-V00241
           END-IF.
           IF WS-V01014 = WS-V01558
               MOVE 'X' TO WS-V00920
           END-IF.
           PERFORM P00120.
           CALL 'PGM041' USING WS-V00777 WS-V01615.
           GO TO P00053.
       P00001.
           MOVE WS-V00192 TO WS-V00999.
           IF WS-V00058 = WS-V01829
               MOVE 'X' TO WS-V01711
           END-IF.
           IF WS-V00798 = WS-V00886
               MOVE 'X' TO WS-V01244
           END-IF.
           IF WS-V01561 = WS-V01571
               MOVE 'X' TO WS-V00004
           END-IF.
           PERFORM P00114.
           CALL 'PGM017' USING WS-V01477 WS-V01642.
           GO TO P00058.
       P00002.
           MOVE WS-V01210 TO WS-V01935.
           IF WS-V00209 = WS-V01846
               MOVE 'X' TO WS-V00650
           END-IF.
           IF WS-V00062 = WS-V00045
               MOVE 'X' TO WS-V00052
           END-IF.
           IF WS-V01330 = WS-V01108
               MOVE 'X' TO WS-V00018
           END-IF.
           PERFORM P00097.
           CALL 'PGM043' USING WS-V00443 WS-V01984.
           GO TO P00108.
       P00003.
           MOVE WS-V01486 TO WS-V00059.
           IF WS-V01080 = WS-V00454
               MOVE 'X' TO WS-V01564
           END-IF.
           IF WS-V00896 = WS-V01923
               MOVE 'X' TO WS-V01015
           END-IF.
           IF WS-V01132 = WS-V00477
               MOVE 'X' TO WS-V00707
           END-IF.
           PERFORM P00059.
           CALL 'PGM043' USING WS-V00448 WS-V01558.
           GO TO P00117.
       P00004.
           MOVE WS-V01950 TO WS-V00593.
           IF WS-V01897 = WS-V00044
               MOVE 'X' TO WS-V00852
           END-IF.
           IF WS-V01715 = WS-V01876
               MOVE 'X' TO WS-V01139
           END-IF.
           IF WS-V01888 = WS-V01315
               MOVE 'X' TO WS-V00204
           END-IF.
           PERFORM P00047.
           CALL 'PGM040' USING WS-V01482 WS-V01761.
           GO TO P00075.
       P00005.
           MOVE WS-V00247 TO WS-V01521.
           IF WS-V00681 = WS-V01834
               MOVE 'X' TO WS-V01477
           END-IF.
           IF WS-V01993 = WS-V01456
               MOVE 'X' TO WS-V01025
           END-IF.
           IF WS-V01917 = WS-V01980
               MOVE 'X' TO WS-V00864
           END-IF.
           PERFORM P00129.
           CALL 'PGM042' USING WS-V00388 WS-V00621.
           GO TO P00072.
       P00006.
           MOVE WS-V01203 TO WS-V01993.
           IF WS-V01807 = WS-V01022
               MOVE 'X' TO WS-V01733
           END-IF.
           IF WS-V01926 = WS-V01034
               MOVE 'X' TO WS-V00805
           END-IF.
           IF WS-V01206 = WS-V01747
               MOVE 'X' TO WS-V00070
           END-IF.
           PERFORM P00122.
           CALL 'PGM015' USING WS-V01523 WS-V01633.
           GO TO P00103.
       P00007.
           MOVE WS-V00848 TO WS-V01361.
           IF WS-V00354 = WS-V00751
               MOVE 'X' TO WS-V01123
           END-IF.
           IF WS-V01807 = WS-V01439
               MOVE 'X' TO WS-V01588
           END-IF.
           IF WS-V01381 = WS-V01511
               MOVE 'X' TO WS-V00767
           END-IF.
           PERFORM P00022.
           CALL 'PGM028' USING WS-V01359 WS-V01041.
           GO TO P00027.
       P00008.
           MOVE WS-V01594 TO WS-V00335.
           IF WS-V01066 = WS-V01720
               MOVE 'X' TO WS-V00805
           END-IF.
           IF WS-V00758 = WS-V01002
               MOVE 'X' TO WS-V01500
           END-IF.
           IF WS-V00060 = WS-V00961
               MOVE 'X' TO WS-V00089
           END-IF.
           PERFORM P00078.
           CALL 'PGM045' USING WS-V01737 WS-V01259.
           GO TO P00148.
       P00009.
           MOVE WS-V00806 TO WS-V01325.
           IF WS-V00348 = WS-V00345
               MOVE 'X' TO WS-V01028
           END-IF.
           IF WS-V00464 = WS-V00025
               MOVE 'X' TO WS-V01578
           END-IF.
           IF WS-V00408 = WS-V01105
               MOVE 'X' TO WS-V01884
           END-IF.
           PERFORM P00140.
           CALL 'PGM014' USING WS-V00828 WS-V01052.
           GO TO P00088.
       P00010.
           MOVE WS-V01950 TO WS-V01735.
           IF WS-V01183 = WS-V00723
               MOVE 'X' TO WS-V00940
           END-IF.
           IF WS-V01863 = WS-V00551
               MOVE 'X' TO WS-V01350
           END-IF.
           IF WS-V01122 = WS-V01247
               MOVE 'X' TO WS-V01960
           END-IF.
           PERFORM P00001.
           CALL 'PGM024' USING WS-V01604 WS-V01755.
           GO TO P00131.
       P00011.
           MOVE WS-V01657 TO WS-V00264.
           IF WS-V01062 = WS-V01592
               MOVE 'X' TO WS-V01149
           END-IF.
           IF WS-V00420 = WS-V00872
               MOVE 'X' TO WS-V01945
           END-IF.
           IF WS-V00114 = WS-V00985
               MOVE 'X' TO WS-V01781
           END-IF.
           PERFORM P00093.
           CALL 'PGM036' USING WS-V01135 WS-V00409.
           GO TO P00129.
       P00012.
           MOVE WS-V00846 TO WS-V00993.
           IF WS-V01665 = WS-V00730
               MOVE 'X' TO WS-V00848
           END-IF.
           IF WS-V00708 = WS-V00003
               MOVE 'X' TO WS-V01102
           END-IF.
           IF WS-V01106 = WS-V01276
               MOVE 'X' TO WS-V01610
           END-IF.
           PERFORM P00084.
           CALL 'PGM029' USING WS-V01228 WS-V00057.
           GO TO P00058.
       P00013.
           MOVE WS-V01301 TO WS-V00362.
           IF WS-V01127 = WS-V01196
               MOVE 'X' TO WS-V00370
           END-IF.
           IF WS-V01763 = WS-V00187
               MOVE 'X' TO WS-V01635
           END-IF.
           IF WS-V01128 = WS-V01632
               MOVE 'X' TO WS-V01743
           END-IF.
           PERFORM P00065.
           CALL 'PGM002' USING WS-V01723 WS-V01932.
           GO TO P00018.
       P00014.
           MOVE WS-V00170 TO WS-V01777.
           IF WS-V00034 = WS-V00927
               MOVE 'X' TO WS-V00029
           END-IF.
           IF WS-V01544 = WS-V01547
               MOVE 'X' TO WS-V00575
           END-IF.
           IF WS-V00511 = WS-V00550
               MOVE 'X' TO WS-V00224
           END-IF.
           PERFORM P00047.
           CALL 'PGM022' USING WS-V00594 WS-V00142.
           GO TO P00042.
       P00015.
           MOVE WS-V00326 TO WS-V00522.
           IF WS-V01080 = WS-V01949
               MOVE 'X' TO WS-V00344
           END-IF.
           IF WS-V01344 = WS-V00558
               MOVE 'X' TO WS-V01327
           END-IF.
           IF WS-V01457 = WS-V00603
               MOVE 'X' TO WS-V00931
           END-IF.
           PERFORM P00082.
           CALL 'PGM031' USING WS-V00970 WS-V00233.
           GO TO P00006.
       P00016.
           MOVE WS-V00638 TO WS-V00791.
           IF WS-V00703 = WS-V00862
               MOVE 'X' TO WS-V01630
           END-IF.
           IF WS-V00385 = WS-V00529
               MOVE 'X' TO WS-V00222
           END-IF.
           IF WS-V00519 = WS-V01842
               MOVE 'X' TO WS-V01495
           END-IF.
           PERFORM P00130.
           CALL 'PGM013' USING WS-V01977 WS-V01240.
           GO TO P00110.
       P00017.
           MOVE WS-V01673 TO WS-V01997.
           IF WS-V00042 = WS-V00461
               MOVE 'X' TO WS-V00036
           END-IF.
           IF WS-V00813 = WS-V00299
               MOVE 'X' TO WS-V00072
           END-IF.
           IF WS-V01472 = WS-V01965
               MOVE 'X' TO WS-V00328
           END-IF.
           PERFORM P00114.
           CALL 'PGM045' USING WS-V01036 WS-V01388.
           GO TO P00109.
       P00018.
           MOVE WS-V01115 TO WS-V01704.
           IF WS-V00451 = WS-V01998
               MOVE 'X' TO WS-V01291
           END-IF.
           IF WS-V01633 = WS-V01423
               MOVE 'X' TO WS-V01057
           END-IF.
           IF WS-V00923 = WS-V00457
               MOVE 'X' TO WS-V01072
           END-IF.
           PERFORM P00007.
           CALL 'PGM025' USING WS-V01382 WS-V01179.
           GO TO P00082.
       P00019.
           MOVE WS-V01351 TO WS-V01292.
           IF WS-V00873 = WS-V00120
               MOVE 'X' TO WS-V01510
           END-IF.
           IF WS-V00611 = WS-V00257
               MOVE 'X' TO WS-V01982
           END-IF.
           IF WS-V00434 = WS-V01793
               MOVE 'X' TO WS-V00097
           END-IF.
           PERFORM P00078.
           CALL 'PGM004' USING WS-V01758 WS-V00156.
           GO TO P00079.
       P00020.
           MOVE WS-V01878 TO WS-V01923.
           IF WS-V00610 = WS-V01523
               MOVE 'X' TO WS-V00324
           END-IF.
           IF WS-V00852 = WS-V01156
               MOVE 'X' TO WS-V00516
           END-IF.
           IF WS-V00267 = WS-V00017
               MOVE 'X' TO WS-V01148
           END-IF.
           PERFORM P00009.
           CALL 'PGM037' USING WS-V01678 WS-V00445.
           GO TO P00145.
       P00021.
           MOVE WS-V00943 TO WS-V00351.
           IF WS-V01695 = WS-V01777
               MOVE 'X' TO WS-V01781
           END-IF.
           IF WS-V01994 = WS-V01597
               MOVE 'X' TO WS-V01441
           END-IF.
           IF WS-V01275 = WS-V01042
               MOVE 'X' TO WS-V00076
           END-IF.
           PERFORM P00096.
           CALL 'PGM012' USING WS-V00710 WS-V00202.
           GO TO P00052.
       P00022.
           MOVE WS-V01174 TO WS-V01380.
           IF WS-V01836 = WS-V00886
               MOVE 'X' TO WS-V01211
           END-IF.
           IF WS-V00397 = WS-V01008
               MOVE 'X' TO WS-V00213
           END-IF.
           IF WS-V01920 = WS-V01363
               MOVE 'X' TO WS-V00798
           END-IF.
           PERFORM P00075.
           CALL 'PGM032' USING WS-V01023 WS-V00035.
           GO TO P00083.
       P00023.
           MOVE WS-V01253 TO WS-V01785.
           IF WS-V00823 = WS-V01842
               MOVE 'X' TO WS-V00576
           END-IF.
           IF WS-V00037 = WS-V00321
               MOVE 'X' TO WS-V00411
           END-IF.
           IF WS-V01756 = WS-V00671
               MOVE 'X' TO WS-V01661
           END-IF.
           PERFORM P00144.
           CALL 'PGM008' USING WS-V00694 WS-V00879.
           GO TO P00054.
       P00024.
           MOVE WS-V00545 TO WS-V01381.
           IF WS-V00197 = WS-V01715
               MOVE 'X' TO WS-V00776
           END-IF.
           IF WS-V01909 = WS-V01121
               MOVE 'X' TO WS-V00704
           END-IF.
           IF WS-V01872 = WS-V01807
               MOVE 'X' TO WS-V01715
           END-IF.
           PERFORM P00136.
           CALL 'PGM031' USING WS-V01572 WS-V01090.
           GO TO P00060.
       P00025.
           MOVE WS-V00133 TO WS-V01485.
           IF WS-V00082 = WS-V00173
               MOVE 'X' TO WS-V00272
           END-IF.
           IF WS-V00347 = WS-V00341
               MOVE 'X' TO WS-V01865
           END-IF.
           IF WS-V01102 = WS-V00436
               MOVE 'X' TO WS-V00548
           END-IF.
           PERFORM P00085.
           CALL 'PGM038' USING WS-V01036 WS-V01722.
           GO TO P00065.
       P00026.
           MOVE WS-V00753 TO WS-V00693.
           IF WS-V00696 = WS-V00233
               MOVE 'X' TO WS-V00596
           END-IF.
           IF WS-V00481 = WS-V01776
               MOVE 'X' TO WS-V01933
           END-IF.
           IF WS-V01236 = WS-V01596
               MOVE 'X' TO WS-V01954
           END-IF.
           PERFORM P00125.
           CALL 'PGM008' USING WS-V01187 WS-V01128.
           GO TO P00026.
       P00027.
           MOVE WS-V00656 TO WS-V00080.
           IF WS-V00832 = WS-V00149
               MOVE 'X' TO WS-V00778
           END-IF.
           IF WS-V01773 = WS-V01614
               MOVE 'X' TO WS-V00301
           END-IF.
           IF WS-V01696 = WS-V00256
               MOVE 'X' TO WS-V00698
           END-IF.
           PERFORM P00029.
           CALL 'PGM039' USING WS-V01203 WS-V01601.
           GO TO P00096.
       P00028.
           MOVE WS-V00156 TO WS-V01168.
           IF WS-V01126 = WS-V00458
               MOVE 'X' TO WS-V01159
           END-IF.
           IF WS-V00167 = WS-V01950
               MOVE 'X' TO WS-V00546
           END-IF.
           IF WS-V00747 = WS-V01824
               MOVE 'X' TO WS-V00605
           END-IF.
           PERFORM P00144.
           CALL 'PGM034' USING WS-V01894 WS-V00234.
           GO TO P00117.
       P00029.
           MOVE WS-V01836 TO WS-V00567.
           IF WS-V00220 = WS-V01611
               MOVE 'X' TO WS-V00093
           END-IF.
           IF WS-V01695 = WS-V00605
               MOVE 'X' TO WS-V00025
           END-IF.
           IF WS-V01256 = WS-V01373
               MOVE 'X' TO WS-V00029
           END-IF.
           PERFORM P00023.
           CALL 'PGM026' USING WS-V00235 WS-V01691.
           GO TO P00010.
       P00030.
           MOVE WS-V00384 TO WS-V00490.
           IF WS-V01608 = WS-V01201
               MOVE 'X' TO WS-V00862
           END-IF.
           IF WS-V00331 = WS-V00236
               MOVE 'X' TO WS-V00923
           END-IF.
           IF WS-V00342 = WS-V01394
               MOVE 'X' TO WS-V00494
           END-IF.
           PERFORM P00040.
           CALL 'PGM047' USING WS-V01730 WS-V00210.
           GO TO P00111.
       P00031.
           MOVE WS-V01865 TO WS-V01975.
           IF WS-V00774 = WS-V01651
               MOVE 'X' TO WS-V01987
           END-IF.
           IF WS-V01111 = WS-V01862
               MOVE 'X' TO WS-V01675
           END-IF.
           IF WS-V00602 = WS-V01126
               MOVE 'X' TO WS-V00518
           END-IF.
           PERFORM P00122.
           CALL 'PGM020' USING WS-V00205 WS-V00425.
           GO TO P00081.
       P00032.
           MOVE WS-V00081 TO WS-V00055.
           IF WS-V00021 = WS-V01611
               MOVE 'X' TO WS-V01895
           END-IF.
           IF WS-V00605 = WS-V01487
               MOVE 'X' TO WS-V01221
           END-IF.
           IF WS-V00655 = WS-V00921
               MOVE 'X' TO WS-V00801
           END-IF.
           PERFORM P00080.
           CALL 'PGM025' USING WS-V00128 WS-V00131.
           GO TO P00081.
       P00033.
           MOVE WS-V01986 TO WS-V01231.
           IF WS-V01986 = WS-V00933
               MOVE 'X' TO WS-V00228
           END-IF.
           IF WS-V00512 = WS-V00440
               MOVE 'X' TO WS-V01607
           END-IF.
           IF WS-V01265 = WS-V01593
               MOVE 'X' TO WS-V01825
           END-IF.
           PERFORM P00138.
           CALL 'PGM044' USING WS-V00960 WS-V01355.
           GO TO P00091.
       P00034.
           MOVE WS-V00530 TO WS-V00375.
           IF WS-V01109 = WS-V00425
               MOVE 'X' TO WS-V00629
           END-IF.
           IF WS-V00407 = WS-V00504
               MOVE 'X' TO WS-V00738
           END-IF.
           IF WS-V00166 = WS-V01679
               MOVE 'X' TO WS-V00575
           END-IF.
           PERFORM P00022.
           CALL 'PGM048' USING WS-V00917 WS-V00185.
           GO TO P00147.
       P00035.
           MOVE WS-V01317 TO WS-V00694.
           IF WS-V01926 = WS-V00465
               MOVE 'X' TO WS-V00799
           END-IF.
           IF WS-V01979 = WS-V00628
               MOVE 'X' TO WS-V00084
           END-IF.
           IF WS-V00670 = WS-V00382
               MOVE 'X' TO WS-V00648
           END-IF.
           PERFORM P00148.
           CALL 'PGM019' USING WS-V00503 WS-V00684.
           GO TO P00025.
       P00036.
           MOVE WS-V01114 TO WS-V01252.
           IF WS-V01185 = WS-V01653
               MOVE 'X' TO WS-V01220
           END-IF.
           IF WS-V00188 = WS-V00501
               MOVE 'X' TO WS-V00450
           END-IF.
           IF WS-V00041 = WS-V01655
               MOVE 'X' TO WS-V00499
           END-IF.
           PERFORM P00102.
           CALL 'PGM004' USING WS-V00548 WS-V01128.
           GO TO P00018.
       P00037.
           MOVE WS-V01493 TO WS-V00153.
           IF WS-V00044 = WS-V01301
               MOVE 'X' TO WS-V00020
           END-IF.
           IF WS-V00595 = WS-V01537
               MOVE 'X' TO WS-V01622
           END-IF.
           IF WS-V00735 = WS-V01010
               MOVE 'X' TO WS-V00960
           END-IF.
           PERFORM P00039.
           CALL 'PGM006' USING WS-V01026 WS-V01592.
           GO TO P00083.
       P00038.
           MOVE WS-V00157 TO WS-V01042.
           IF WS-V01944 = WS-V01362
               MOVE 'X' TO WS-V00354
           END-IF.
           IF WS-V00367 = WS-V01589
               MOVE 'X' TO WS-V00306
           END-IF.
           IF WS-V00289 = WS-V01682
               MOVE 'X' TO WS-V01772
           END-IF.
           PERFORM P00081.
           CALL 'PGM019' USING WS-V00218 WS-V01452.
           GO TO P00131.
       P00039.
           MOVE WS-V01709 TO WS-V01882.
           IF WS-V01232 = WS-V00601
               MOVE 'X' TO WS-V00258
           END-IF.
           IF WS-V01830 = WS-V00423
               MOVE 'X' TO WS-V00290
           END-IF.
           IF WS-V01117 = WS-V01864
               MOVE 'X' TO WS-V01479
           END-IF.
           PERFORM P00008.
           CALL 'PGM049' USING WS-V00647 WS-V01681.
           GO TO P00141.
       P00040.
           MOVE WS-V01721 TO WS-V01932.
           IF WS-V01528 = WS-V01412
               MOVE 'X' TO WS-V00420
           END-IF.
           IF WS-V00364 = WS-V00612
               MOVE 'X' TO WS-V00886
           END-IF.
           IF WS-V01100 = WS-V00323
               MOVE 'X' TO WS-V00099
           END-IF.
           PERFORM P00063.
           CALL 'PGM016' USING WS-V01592 WS-V00131.
           GO TO P00114.
       P00041.
           MOVE WS-V01655 TO WS-V00880.
           IF WS-V01124 = WS-V00512
               MOVE 'X' TO WS-V01108
           END-IF.
           IF WS-V00899 = WS-V01743
               MOVE 'X' TO WS-V01101
           END-IF.
           IF WS-V00928 = WS-V00022
               MOVE 'X' TO WS-V00810
           END-IF.
           PERFORM P00086.
           CALL 'PGM010' USING WS-V00528 WS-V00994.
           GO TO P00006.
       P00042.
           MOVE WS-V01624 TO WS-V01323.
           IF WS-V01910 = WS-V00853
               MOVE 'X' TO WS-V01999
           END-IF.
           IF WS-V01168 = WS-V00038
               MOVE 'X' TO WS-V00127
           END-IF.
           IF WS-V01416 = WS-V00726
               MOVE 'X' TO WS-V01187
           END-IF.
           PERFORM P00035.
           CALL 'PGM037' USING WS-V00256 WS-V00283.
           GO TO P00066.
       P00043.
           MOVE WS-V01697 TO WS-V00567.
           IF WS-V00814 = WS-V01155
               MOVE 'X' TO WS-V00821
           END-IF.
           IF WS-V00352 = WS-V01254
               MOVE 'X' TO WS-V00182
           END-IF.
           IF WS-V00478 = WS-V00995
               MOVE 'X' TO WS-V00015
           END-IF.
           PERFORM P00045.
           CALL 'PGM033' USING WS-V00649 WS-V01025.
           GO TO P00112.
       P00044.
           MOVE WS-V01904 TO WS-V01405.
           IF WS-V01308 = WS-V01497
               MOVE 'X' TO WS-V00462
           END-IF.
           IF WS-V00488 = WS-V00640
               MOVE 'X' TO WS-V01013
           END-IF.
           IF WS-V01406 = WS-V00980
               MOVE 'X' TO WS-V01958
           END-IF.
           PERFORM P00057.
           CALL 'PGM045' USING WS-V00844 WS-V00690.
           GO TO P00143.
       P00045.
           MOVE WS-V01251 TO WS-V01857.
           IF WS-V01491 = WS-V01879
               MOVE 'X' TO WS-V01338
           END-IF.
           IF WS-V00563 = WS-V01991
               MOVE 'X' TO WS-V01323
           END-IF.
           IF WS-V00449 = WS-V00098
               MOVE 'X' TO WS-V01887
           END-IF.
           PERFORM P00018.
           CALL 'PGM048' USING WS-V01047 WS-V01321.
           GO TO P00094.
       P00046.
           MOVE WS-V00326 TO WS-V01047.
           IF WS-V01568 = WS-V01622
               MOVE 'X' TO WS-V01808
           END-IF.
           IF WS-V00417 = WS-V00638
               MOVE 'X' TO WS-V00611
           END-IF.
           IF WS-V01418 = WS-V00613
               MOVE 'X' TO WS-V01738
           END-IF.
           PERFORM P00141.
           CALL 'PGM023' USING WS-V00338 WS-V01436.
           GO TO P00118.
       P00047.
           MOVE WS-V01217 TO WS-V00174.
           IF WS-V01753 = WS-V00252
               MOVE 'X' TO WS-V01836
           END-IF.
           IF WS-V01241 = WS-V01966
               MOVE 'X' TO WS-V01052
           END-IF.
           IF WS-V01169 = WS-V00772
               MOVE 'X' TO WS-V00361
           END-IF.
           PERFORM P00039.
           CALL 'PGM016' USING WS-V00873 WS-V00445.
           GO TO P00145.
       P00048.
           MOVE WS-V01473 TO WS-V01551.
           IF WS-V01602 = WS-V00106
               MOVE 'X' TO WS-V01013
           END-IF.
           IF WS-V01395 = WS-V00806
               MOVE 'X' TO WS-V01468
           END-IF.
           IF WS-V01304 = WS-V00712
               MOVE 'X' TO WS-V00786
           END-IF.
           PERFORM P00131.
           CALL 'PGM010' USING WS-V01114 WS-V01494.
           GO TO P00010.
       P00049.
           MOVE WS-V01073 TO WS-V00185.
           IF WS-V01654 = WS-V00522
               MOVE 'X' TO WS-V01287
           END-IF.
           IF WS-V00206 = WS-V00547
               MOVE 'X' TO WS-V01509
           END-IF.
           IF WS-V01869 = WS-V00171
               MOVE 'X' TO WS-V01964
           END-IF.
           PERFORM P00035.
           CALL 'PGM049' USING WS-V01263 WS-V01724.
           GO TO P00020.
       P00050.
           MOVE WS-V00911 TO WS-V01742.
           IF WS-V01892 = WS-V00493
               MOVE 'X' TO WS-V01989
           END-IF.
           IF WS-V01742 = WS-V00783
               MOVE 'X' TO WS-V01925
           END-IF.
           IF WS-V01643 = WS-V01850
               MOVE 'X' TO WS-V00886
           END-IF.
           PERFORM P00101.
           CALL 'PGM010' USING WS-V01863 WS-V00666.
           GO TO P00112.
       P00051.
           MOVE WS-V00258 TO WS-V01274.
           IF WS-V01861 = WS-V00999
               MOVE 'X' TO WS-V01964
           END-IF.
           IF WS-V00434 = WS-V00244
               MOVE 'X' TO WS-V00883
           END-IF.
           IF WS-V01230 = WS-V01093
               MOVE 'X' TO WS-V00836
           END-IF.
           PERFORM P00030.
           CALL 'PGM042' USING WS-V00605 WS-V00568.
           GO TO P00063.
       P00052.
           MOVE WS-V00775 TO WS-V01535.
           IF WS-V01145 = WS-V00008
               MOVE 'X' TO WS-V01965
           END-IF.
           IF WS-V00388 = WS-V01082
               MOVE 'X' TO WS-V00898
           END-IF.
           IF WS-V01185 = WS-V00043
               MOVE 'X' TO WS-V00063
           END-IF.
           PERFORM P00062.
           CALL 'PGM016' USING WS-V00423 WS-V00354.
           GO TO P00072.
       P00053.
           MOVE WS-V00303 TO WS-V01110.
           IF WS-V00410 = WS-V00559
               MOVE 'X' TO WS-V00637
           END-IF.
           IF WS-V01199 = WS-V01551
               MOVE 'X' TO WS-V00513
           END-IF.
           IF WS-V01704 = WS-V01399
               MOVE 'X' TO WS-V00914
           END-IF.
           PERFORM P00043.
           CALL 'PGM034' USING WS-V00731 WS-V01005.
           GO TO P00107.
       P00054.
           MOVE WS-V01752 TO WS-V00249.
           IF WS-V01575 = WS-V00427
               MOVE 'X' TO WS-V01168
           END-IF.
           IF WS-V01800 = WS-V00784
               MOVE 'X' TO WS-V00419
           END-IF.
           IF WS-V00581 = WS-V01660
               MOVE 'X' TO WS-V00221
           END-IF.
           PERFORM P00006.
           CALL 'PGM007' USING WS-V01165 WS-V01530.
           GO TO P00003.
       P00055.
           MOVE WS-V01116 TO WS-V00607.
           IF WS-V01976 = WS-V01380
               MOVE 'X' TO WS-V01558
           END-IF.
           IF WS-V01483 = WS-V01993
               MOVE 'X' TO WS-V01329
           END-IF.
           IF WS-V00279 = WS-V00153
               MOVE 'X' TO WS-V01024
           END-IF.
           PERFORM P00095.
           CALL 'PGM036' USING WS-V01649 WS-V00637.
           GO TO P00111.
       P00056.
           MOVE WS-V01030 TO WS-V01387.
           IF WS-V00730 = WS-V01553
               MOVE 'X' TO WS-V01082
           END-IF.
           IF WS-V00662 = WS-V00001
               MOVE 'X' TO WS-V00253
           END-IF.
           IF WS-V00905 = WS-V01470
               MOVE 'X' TO WS-V00920
           END-IF.
           PERFORM P00089.
           CALL 'PGM019' USING WS-V01104 WS-V00817.
           GO TO P00086.
       P00057.
           MOVE WS-V01603 TO WS-V01497.
           IF WS-V01399 = WS-V01170
               MOVE 'X' TO WS-V01008
           END-IF.
           IF WS-V00231 = WS-V01326
               MOVE 'X' TO WS-V01879
           END-IF.
           IF WS-V00773 = WS-V00783
               MOVE 'X' TO WS-V00417
           END-IF.
           PERFORM P00142.
           CALL 'PGM000' USING WS-V00568 WS-V01301.
           GO TO P00130.
       P00058.
           MOVE WS-V00407 TO WS-V01890.
           IF WS-V00945 = WS-V01230
               MOVE 'X' TO WS-V01709
           END-IF.
           IF WS-V01058 = WS-V00837
               MOVE 'X' TO WS-V01919
           END-IF.
           IF WS-V01525 = WS-V01458
               MOVE 'X' TO WS-V00625
           END-IF.
           PERFORM P00043.
           CALL 'PGM028' USING WS-V01269 WS-V01369.
           GO TO P00135.
       P00059.
           MOVE WS-V00404 TO WS-V00736.
           IF WS-V01077 = WS-V00007
               MOVE 'X' TO WS-V01389
           END-IF.
           IF WS-V00797 = WS-V01186
               MOVE 'X' TO WS-V00872
           END-IF.
           IF WS-V01987 = WS-V00829
               MOVE 'X' TO WS-V00688
           END-IF.
           PERFORM P00149.
           CALL 'PGM046' USING WS-V01432 WS-V01838.
           GO TO P00017.
       P00060.
           MOVE WS-V01009 TO WS-V01527.
           IF WS-V00507 = WS-V01311
               MOVE 'X' TO WS-V01980
           END-IF.
           IF WS-V01328 = WS-V00595
               MOVE 'X' TO WS-V01289
           END-IF.
           IF WS-V00042 = WS-V00833
               MOVE 'X' TO WS-V01477
           END-IF.
           PERFORM P00039.
           CALL 'PGM040' USING WS-V01595 WS-V01919.
           GO TO P00101.
       P00061.
           MOVE WS-V01602 TO WS-V00553.
           IF WS-V01733 = WS-V00364
               MOVE 'X' TO WS-V01571
           END-IF.
           IF WS-V00150 = WS-V01669
               MOVE 'X' TO WS-V01589
           END-IF.
           IF WS-V01239 = WS-V00020
               MOVE 'X' TO WS-V00715
           END-IF.
           PERFORM P00067.
           CALL 'PGM045' USING WS-V00842 WS-V01789.
           GO TO P00139.
       P00062.
           MOVE WS-V00621 TO WS-V00311.
           IF WS-V00946 = WS-V01705
               MOVE 'X' TO WS-V00531
           END-IF.
           IF WS-V00992 = WS-V00347
               MOVE 'X' TO WS-V00956
           END-IF.
           IF WS-V01045 = WS-V00092
               MOVE 'X' TO WS-V00554
           END-IF.
           PERFORM P00130.
           CALL 'PGM006' USING WS-V01525 WS-V01209.
           GO TO P00108.
       P00063.
           MOVE WS-V00142 TO WS-V00727.
           IF WS-V00137 = WS-V01345
               MOVE 'X' TO WS-V00906
           END-IF.
           IF WS-V00040 = WS-V00336
               MOVE 'X' TO WS-V01038
           END-IF.
           IF WS-V01454 = WS-V01937
               MOVE 'X' TO WS-V00331
           END-IF.
           PERFORM P00023.
           CALL 'PGM025' USING WS-V01302 WS-V01410.
           GO TO P00070.
       P00064.
           MOVE WS-V01239 TO WS-V00623.
           IF WS-V00427 = WS-V01081
               MOVE 'X' TO WS-V00425
           END-IF.
           IF WS-V00485 = WS-V01814
               MOVE 'X' TO WS-V00683
           END-IF.
           IF WS-V00551 = WS-V00140
               MOVE 'X' TO WS-V00153
           END-IF.
           PERFORM P00133.
           CALL 'PGM042' USING WS-V00754 WS-V00958.
           GO TO P00130.
       P00065.
           MOVE WS-V01142 TO WS-V01508.
           IF WS-V00101 = WS-V00345
               MOVE 'X' TO WS-V00608
           END-IF.
           IF WS-V01337 = WS-V01505
               MOVE 'X' TO WS-V01461
           END-IF.
           IF WS-V01669 = WS-V01139
               MOVE 'X' TO WS-V00552
           END-IF.
           PERFORM P00091.
           CALL 'PGM039' USING WS-V01515 WS-V00475.
           GO TO P00100.
       P00066.
           MOVE WS-V01149 TO WS-V00818.
           IF WS-V00352 = WS-V00990
               MOVE 'X' TO WS-V01617
           END-IF.
           IF WS-V00531 = WS-V01775
               MOVE 'X' TO WS-V01250
           END-IF.
           IF WS-V00675 = WS-V01466
               MOVE 'X' TO WS-V00455
           END-IF.
           PERFORM P00066.
           CALL 'PGM039' USING WS-V01447 WS-V00500.
           GO TO P00007.
       P00067.
           MOVE WS-V01744 TO WS-V01841.
           IF WS-V01778 = WS-V01274
               MOVE 'X' TO WS-V00824
           END-IF.
           IF WS-V00648 = WS-V01900
               MOVE 'X' TO WS-V00884
           END-IF.
           IF WS-V01911 = WS-V01559
               MOVE 'X' TO WS-V00508
           END-IF.
           PERFORM P00068.
           CALL 'PGM012' USING WS-V00148 WS-V01281.
           GO TO P00042.
       P00068.
           MOVE WS-V01783 TO WS-V01992.
           IF WS-V01186 = WS-V00908
               MOVE 'X' TO WS-V01190
           END-IF.
           IF WS-V01870 = WS-V01910
               MOVE 'X' TO WS-V01491
           END-IF.
           IF WS-V00303 = WS-V01241
               MOVE 'X' TO WS-V01936
           END-IF.
           PERFORM P00067.
           CALL 'PGM029' USING WS-V01078 WS-V00332.
           GO TO P00035.
       P00069.
           MOVE WS-V01594 TO WS-V00282.
           IF WS-V01830 = WS-V01465
               MOVE 'X' TO WS-V00902
           END-IF.
           IF WS-V00739 = WS-V00634
               MOVE 'X' TO WS-V01538
           END-IF.
           IF WS-V00820 = WS-V00492
               MOVE 'X' TO WS-V00237
           END-IF.
           PERFORM P00052.
           CALL 'PGM045' USING WS-V01395 WS-V00625.
           GO TO P00017.
       P00070.
           MOVE WS-V00217 TO WS-V00466.
           IF WS-V00813 = WS-V00658
               MOVE 'X' TO WS-V01008
           END-IF.
           IF WS-V01901 = WS-V00204
               MOVE 'X' TO WS-V01956
           END-IF.
           IF WS-V00382 = WS-V00092
               MOVE 'X' TO WS-V00113
           END-IF.
           PERFORM P00005.
           CALL 'PGM048' USING WS-V00443 WS-V01399.
           GO TO P00008.
       P00071.
           MOVE WS-V01012 TO WS-V01441.
           IF WS-V01082 = WS-V01668
               MOVE 'X' TO WS-V01482
           END-IF.
           IF WS-V01974 = WS-V01816
               MOVE 'X' TO WS-V01255
           END-IF.
           IF WS-V00905 = WS-V00701
               MOVE 'X' TO WS-V01357
           END-IF.
           PERFORM P00070.
           CALL 'PGM007' USING WS-V01255 WS-V01418.
           GO TO P00044.
       P00072.
           MOVE WS-V00195 TO WS-V00454.
           IF WS-V00818 = WS-V00477
               MOVE 'X' TO WS-V01013
           END-IF.
           IF WS-V00921 = WS-V00773
               MOVE 'X' TO WS-V01537
           END-IF.
           IF WS-V00345 = WS-V01993
               MOVE 'X' TO WS-V00474
           END-IF.
           PERFORM P00060.
           CALL 'PGM018' USING WS-V00947 WS-V01120.
           GO TO P00148.
       P00073.
           MOVE WS-V00797 TO WS-V00433.
           IF WS-V00925 = WS-V01464
               MOVE 'X' TO WS-V00528
           END-IF.
           IF WS-V00676 = WS-V01016
               MOVE 'X' TO WS-V01215
           END-IF.
           IF WS-V00227 = WS-V01862
               MOVE 'X' TO WS-V00437
           END-IF.
           PERFORM P00020.
           CALL 'PGM002' USING WS-V00031 WS-V01633.
           GO TO P00001.
       P00074.
           MOVE WS-V01756 TO WS-V00983.
           IF WS-V00654 = WS-V01820
               MOVE 'X' TO WS-V00784
           END-IF.
           IF WS-V01736 = WS-V01188
               MOVE 'X' TO WS-V00588
           END-IF.
           IF WS-V01881 = WS-V00401
               MOVE 'X' TO WS-V00819
           END-IF.
           PERFORM P00040.
           CALL 'PGM048' USING WS-V01323 WS-V00311.
           GO TO P00007.
       P00075.
           MOVE WS-V00031 TO WS-V00793.
           IF WS-V00297 = WS-V01794
               MOVE 'X' TO WS-V01361
           END-IF.
           IF WS-V01111 = WS-V00116
               MOVE 'X' TO WS-V01156
           END-IF.
           IF WS-V00777 = WS-V00520
               MOVE 'X' TO WS-V00266
           END-IF.
           PERFORM P00020.
           CALL 'PGM029' USING WS-V01335 WS-V01720.
           GO TO P00077.
       P00076.
           MOVE WS-V01855 TO WS-V00029.
           IF WS-V00072 = WS-V01099
               MOVE 'X' TO WS-V00124
           END-IF.
           IF WS-V01075 = WS-V01721
               MOVE 'X' TO WS-V00264
           END-IF.
           IF WS-V00087 = WS-V01911
               MOVE 'X' TO WS-V00560
           END-IF.
           PERFORM P00030.
           CALL 'PGM027' USING WS-V00186 WS-V00389.
           GO TO P00007.
       P00077.
           MOVE WS-V01023 TO WS-V01305.
           IF WS-V00266 = WS-V01524
               MOVE 'X' TO WS-V00571
           END-IF.
           IF WS-V01406 = WS-V01673
               MOVE 'X' TO WS-V01731
           END-IF.
           IF WS-V00393 = WS-V01357
               MOVE 'X' TO WS-V00916
           END-IF.
           PERFORM P00099.
           CALL 'PGM021' USING WS-V01292 WS-V00548.
           GO TO P00066.
       P00078.
           MOVE WS-V01314 TO WS-V01301.
           IF WS-V00497 = WS-V00502
               MOVE 'X' TO WS-V00123
           END-IF.
           IF WS-V01204 = WS-V01915
               MOVE 'X' TO WS-V01613
           END-IF.
           IF WS-V01209 = WS-V00358
               MOVE 'X' TO WS-V00716
           END-IF.
           PERFORM P00109.
           CALL 'PGM038' USING WS-V01429 WS-V01147.
           GO TO P00133.
       P00079.
           MOVE WS-V01984 TO WS-V00124.
           IF WS-V01853 = WS-V00723
               MOVE 'X' TO WS-V01120
           END-IF.
           IF WS-V00845 = WS-V01102
               MOVE 'X' TO WS-V00408
           END-IF.
           IF WS-V01457 = WS-V01802
               MOVE 'X' TO WS-V01098
           END-IF.
           PERFORM P00108.
           CALL 'PGM042' USING WS-V00143 WS-V01461.
           GO TO P00068.
       P00080.
           MOVE WS-V01522 TO WS-V01250.
           IF WS-V01476 = WS-V01991
               MOVE 'X' TO WS-V01540
           END-IF.
           IF WS-V00148 = WS-V00515
               MOVE 'X' TO WS-V00363
           END-IF.
           IF WS-V01999 = WS-V00197
               MOVE 'X' TO WS-V00309
           END-IF.
           PERFORM P00015.
           CALL 'PGM013' USING WS-V01750 WS-V00876.
           GO TO P00011.
       P00081.
           MOVE WS-V00108 TO WS-V01304.
           IF WS-V00186 = WS-V01868
               MOVE 'X' TO WS-V01665
           END-IF.
           IF WS-V01050 = WS-V00960
               MOVE 'X' TO WS-V01026
           END-IF.
           IF WS-V00758 = WS-V00203
               MOVE 'X' TO WS-V00640
           END-IF.
           PERFORM P00010.
           CALL 'PGM008' USING WS-V01088 WS-V00067.
           GO TO P00113.
       P00082.
           MOVE WS-V01360 TO WS-V00262.
           IF WS-V01833 = WS-V00809
               MOVE 'X' TO WS-V01563
           END-IF.
           IF WS-V01449 = WS-V01840
               MOVE 'X' TO WS-V01806
           END-IF.
           IF WS-V00913 = WS-V00050
               MOVE 'X' TO WS-V01508
           END-IF.
           PERFORM P00134.
           CALL 'PGM017' USING WS-V00185 WS-V00512.
           GO TO P00083.
       P00083.
           MOVE WS-V00175 TO WS-V00618.
           IF WS-V00070 = WS-V01760
               MOVE 'X' TO WS-V00786
           END-IF.
           IF WS-V00119 = WS-V01500
               MOVE 'X' TO WS-V00534
           END-IF.
           IF WS-V00641 = WS-V01505
               MOVE 'X' TO WS-V00266
           END-IF.
           PERFORM P00066.
           CALL 'PGM024' USING WS-V01652 WS-V00239.
           GO TO P00077.
       P00084.
           MOVE WS-V00192 TO WS-V00870.
           IF WS-V01723 = WS-V00502
               MOVE 'X' TO WS-V01029
           END-IF.
           IF WS-V01141 = WS-V00420
               MOVE 'X' TO WS-V00676
           END-IF.
           IF WS-V01890 = WS-V00693
               MOVE 'X' TO WS-V01043
           END-IF.
           PERFORM P00100.
           CALL 'PGM037' USING WS-V00985 WS-V00214.
           GO TO P00033.
       P00085.
           MOVE WS-V01336 TO WS-V01668.
           IF WS-V00918 = WS-V01072
               MOVE 'X' TO WS-V01144
           END-IF.
           IF WS-V01473 = WS-V01728
               MOVE 'X' TO WS-V01709
           END-IF.
           IF WS-V01190 = WS-V01436
               MOVE 'X' TO WS-V01065
           END-IF.
           PERFORM P00137.
           CALL 'PGM001' USING WS-V01836 WS-V01703.
           GO TO P00074.
       P00086.
           MOVE WS-V01522 TO WS-V00321.
           IF WS-V00409 = WS-V00758
               MOVE 'X' TO WS-V00797
           END-IF.
           IF WS-V01067 = WS-V00664
               MOVE 'X' TO WS-V00199
           END-IF.
           IF WS-V00838 = WS-V00707
               MOVE 'X' TO WS-V00258
           END-IF.
           PERFORM P00147.
           CALL 'PGM004' USING WS-V00089 WS-V00615.
           GO TO P00136.
       P00087.
           MOVE WS-V00642 TO WS-V00855.
           IF WS-V00610 = WS-V00652
               MOVE 'X' TO WS-V00722
           END-IF.
           IF WS-V00558 = WS-V00666
               MOVE 'X' TO WS-V01533
           END-IF.
           IF WS-V01532 = WS-V01065
               MOVE 'X' TO WS-V01026
           END-IF.
           PERFORM P00002.
           CALL 'PGM033' USING WS-V00249 WS-V00304.
           GO TO P00081.
       P00088.
           MOVE WS-V01872 TO WS-V01488.
           IF WS-V00666 = WS-V01607
               MOVE 'X' TO WS-V00670
           END-IF.
           IF WS-V01173 = WS-V00140
               MOVE 'X' TO WS-V00925
           END-IF.
           IF WS-V00572 = WS-V00982
               MOVE 'X' TO WS-V00930
           END-IF.
           PERFORM P00093.
           CALL 'PGM047' USING WS-V01986 WS-V00779.
           GO TO P00020.
       P00089.
           MOVE WS-V01888 TO WS-V01185.
           IF WS-V01641 = WS-V00114
               MOVE 'X' TO WS-V00275
           END-IF.
           IF WS-V00099 = WS-V01072
               MOVE 'X' TO WS-V01007
           END-IF.
           IF WS-V01178 = WS-V01747
               MOVE 'X' TO WS-V00515
           END-IF.
           PERFORM P00062.
           CALL 'PGM044' USING WS-V01175 WS-V01529.
           GO TO P00086.
       P00090.
           MOVE WS-V00740 TO WS-V01929.
           IF WS-V01632 = WS-V01317
               MOVE 'X' TO WS-V00758
           END-IF.
           IF WS-V00824 = WS-V00629
               MOVE 'X' TO WS-V00951
           END-IF.
           IF WS-V01225 = WS-V00697
               MOVE 'X' TO WS-V01089
           END-IF.
           PERFORM P00129.
           CALL 'PGM010' USING WS-V00059 WS-V00303.
           GO TO P00064.
       P00091.
           MOVE WS-V01407 TO WS-V00452.
           IF WS-V01152 = WS-V00273
               MOVE 'X' TO WS-V01857
           END-IF.
           IF WS-V00230 = WS-V00378
               MOVE 'X' TO WS-V01569
           END-IF.
           IF WS-V00841 = WS-V01923
               MOVE 'X' TO WS-V01490
           END-IF.
           PERFORM P00012.
           CALL 'PGM006' USING WS-V01117 WS-V01395.
           GO TO P00068.
       P00092.
           MOVE WS-V01463 TO WS-V00219.
           IF WS-V00418 = WS-V00535
               MOVE 'X' TO WS-V00136
           END-IF.
           IF WS-V01294 = WS-V01169
               MOVE 'X' TO WS-V01078
           END-IF.
           IF WS-V01312 = WS-V00160
               MOVE 'X' TO WS-V01751
           END-IF.
           PERFORM P00018.
           CALL 'PGM013' USING WS-V01317 WS-V01716.
           GO TO P00044.
       P00093.
           MOVE WS-V01047 TO WS-V01765.
           IF WS-V00884 = WS-V00044
               MOVE 'X' TO WS-V01209
           END-IF.
           IF WS-V00753 = WS-V01843
               MOVE 'X' TO WS-V01735
           END-IF.
           IF WS-V00996 = WS-V01454
               MOVE 'X' TO WS-V01653
           END-IF.
           PERFORM P00072.
           CALL 'PGM014' USING WS-V01825 WS-V00410.
           GO TO P00126.
       P00094.
           MOVE WS-V01772 TO WS-V01840.
           IF WS-V01828 = WS-V00481
               MOVE 'X' TO WS-V00871
           END-IF.
           IF WS-V00926 = WS-V01383
               MOVE 'X' TO WS-V00751
           END-IF.
           IF WS-V01115 = WS-V01869
               MOVE 'X' TO WS-V01934
           END-IF.
           PERFORM P00048.
           CALL 'PGM030' USING WS-V01486 WS-V00148.
           GO TO P00065.
       P00095.
           MOVE WS-V00834 TO WS-V00412.
           IF WS-V00016 = WS-V01529
               MOVE 'X' TO WS-V01089
           END-IF.
           IF WS-V01577 = WS-V00779
               MOVE 'X' TO WS-V01053
           END-IF.
           IF WS-V01794 = WS-V00997
               MOVE 'X' TO WS-V00156
           END-IF.
           PERFORM P00103.
           CALL 'PGM039' USING WS-V01807 WS-V01044.
           GO TO P00148.
       P00096.
           MOVE WS-V01197 TO WS-V00871.
           IF WS-V00082 = WS-V00720
               MOVE 'X' TO WS-V01743
           END-IF.
           IF WS-V00938 = WS-V00013
               MOVE 'X' TO WS-V00388
           END-IF.
           IF WS-V01967 = WS-V00613
               MOVE 'X' TO WS-V01425
           END-IF.
           PERFORM P00001.
           CALL 'PGM034' USING WS-V00245 WS-V01683.
           GO TO P00077.
       P00097.
           MOVE WS-V01049 TO WS-V01816.
           IF WS-V01529 = WS-V00646
               MOVE 'X' TO WS-V01988
           END-IF.
           IF WS-V01590 = WS-V01112
               MOVE 'X' TO WS-V01321
           END-IF.
           IF WS-V01171 = WS-V01129
               MOVE 'X' TO WS-V00578
           END-IF.
           PERFORM P00134.
           CALL 'PGM026' USING WS-V01110 WS-V01924.
           GO TO P00132.
       P00098.
           MOVE WS-V00836 TO WS-V01234.
           IF WS-V01290 = WS-V01190
               MOVE 'X' TO WS-V00630
           END-IF.
           IF WS-V00926 = WS-V00618
               MOVE 'X' TO WS-V00268
           END-IF.
           IF WS-V01036 = WS-V00909
               MOVE 'X' TO WS-V01200
           END-IF.
           PERFORM P00035.
           CALL 'PGM035' USING WS-V01582 WS-V01995.
           GO TO P00041.
       P00099.
           MOVE WS-V00517 TO WS-V01303.
           IF WS-V00019 = WS-V00868
               MOVE 'X' TO WS-V01507
           END-IF.
           IF WS-V01354 = WS-V01158
               MOVE 'X' TO WS-V00074
           END-IF.
           IF WS-V00754 = WS-V00861
               MOVE 'X' TO WS-V00823
           END-IF.
           PERFORM P00072.
           CALL 'PGM042' USING WS-V01833 WS-V01538.
           GO TO P00004.
       P00100.
           MOVE WS-V01840 TO WS-V00185.
           IF WS-V01894 = WS-V00184
               MOVE 'X' TO WS-V01733
           END-IF.
           IF WS-V00009 = WS-V00785
               MOVE 'X' TO WS-V00550
           END-IF.
           IF WS-V00951 = WS-V00556
               MOVE 'X' TO WS-V01630
           END-IF.
           PERFORM P00095.
           CALL 'PGM040' USING WS-V01534 WS-V01745.
           GO TO P00123.
       P00101.
           MOVE WS-V01574 TO WS-V00689.
           IF WS-V00795 = WS-V00934
               MOVE 'X' TO WS-V01645
           END-IF.
           IF WS-V00238 = WS-V00990
               MOVE 'X' TO WS-V00726
           END-IF.
           IF WS-V00296 = WS-V00850
               MOVE 'X' TO WS-V00303
           END-IF.
           PERFORM P00004.
           CALL 'PGM011' USING WS-V01667 WS-V00532.
           GO TO P00094.
       P00102.
           MOVE WS-V01756 TO WS-V00260.
           IF WS-V01207 = WS-V01610
               MOVE 'X' TO WS-V00588
           END-IF.
           IF WS-V01944 = WS-V00845
               MOVE 'X' TO WS-V00528
           END-IF.
           IF WS-V01923 = WS-V01052
               MOVE 'X' TO WS-V00588
           END-IF.
           PERFORM P00107.
           CALL 'PGM044' USING WS-V00560 WS-V00887.
           GO TO P00085.
       P00103.
           MOVE WS-V01591 TO WS-V01871.
           IF WS-V00994 = WS-V00441
               MOVE 'X' TO WS-V01465
           END-IF.
           IF WS-V01699 = WS-V01006
               MOVE 'X' TO WS-V01944
           END-IF.
           IF WS-V01989 = WS-V00823
               MOVE 'X' TO WS-V01466
           END-IF.
           PERFORM P00108.
           CALL 'PGM005' USING WS-V00131 WS-V00265.
           GO TO P00052.
       P00104.
           MOVE WS-V01979 TO WS-V00306.
           IF WS-V00469 = WS-V01495
               MOVE 'X' TO WS-V00053
           END-IF.
           IF WS-V00211 = WS-V00518
               MOVE 'X' TO WS-V00318
           END-IF.
           IF WS-V00982 = WS-V01586
               MOVE 'X' TO WS-V01950
           END-IF.
           PERFORM P00025.
           CALL 'PGM025' USING WS-V01330 WS-V01481.
           GO TO P00047.
       P00105.
           MOVE WS-V01708 TO WS-V00006.
           IF WS-V00182 = WS-V00875
               MOVE 'X' TO WS-V01253
           END-IF.
           IF WS-V01955 = WS-V00104
               MOVE 'X' TO WS-V01125
           END-IF.
           IF WS-V00447 = WS-V01094
               MOVE 'X' TO WS-V00864
           END-IF.
           PERFORM P00088.
           CALL 'PGM003' USING WS-V01937 WS-V01334.
           GO TO P00026.
       P00106.
           MOVE WS-V01504 TO WS-V01132.
           IF WS-V01390 = WS-V00859
               MOVE 'X' TO WS-V01709
           END-IF.
           IF WS-V01375 = WS-V01517
               MOVE 'X' TO WS-V00242
           END-IF.
           IF WS-V00543 = WS-V01401
               MOVE 'X' TO WS-V00570
           END-IF.
           PERFORM P00045.
           CALL 'PGM030' USING WS-V01648 WS-V01624.
           GO TO P00012.
       P00107.
           MOVE WS-V01610 TO WS-V00438.
           IF WS-V01386 = WS-V01319
               MOVE 'X' TO WS-V00178
           END-IF.
           IF WS-V01774 = WS-V00798
               MOVE 'X' TO WS-V00253
           END-IF.
           IF WS-V01369 = WS-V00916
               MOVE 'X' TO WS-V00602
           END-IF.
           PERFORM P00130.
           CALL 'PGM031' USING WS-V01853 WS-V00805.
           GO TO P00029.
       P00108.
           MOVE WS-V01241 TO WS-V01748.
           IF WS-V00981 = WS-V00216
               MOVE 'X' TO WS-V00305
           END-IF.
           IF WS-V00791 = WS-V01256
               MOVE 'X' TO WS-V01854
           END-IF.
           IF WS-V01438 = WS-V00412
               MOVE 'X' TO WS-V00342
           END-IF.
           PERFORM P00133.
           CALL 'PGM016' USING WS-V00853 WS-V01522.
           GO TO P00137.
       P00109.
           MOVE WS-V00591 TO WS-V01779.
           IF WS-V01008 = WS-V01297
               MOVE 'X' TO WS-V01832
           END-IF.
           IF WS-V01659 = WS-V01115
               MOVE 'X' TO WS-V01869
           END-IF.
           IF WS-V00439 = WS-V01615
               MOVE 'X' TO WS-V01555
           END-IF.
           PERFORM P00086.
           CALL 'PGM031' USING WS-V00210 WS-V00017.
           GO TO P00088.
       P00110.
           MOVE WS-V01896 TO WS-V01812.
           IF WS-V01983 = WS-V01451
               MOVE 'X' TO WS-V00547
           END-IF.
           IF WS-V00115 = WS-V01107
               MOVE 'X' TO WS-V01280
           END-IF.
           IF WS-V00901 = WS-V00614
               MOVE 'X' TO WS-V01556
           END-IF.
           PERFORM P00025.
           CALL 'PGM014' USING WS-V01040 WS-V00562.
           GO TO P00069.
       P00111.
           MOVE WS-V01446 TO WS-V00504.
           IF WS-V00843 = WS-V00303
               MOVE 'X' TO WS-V00266
           END-IF.
           IF WS-V00524 = WS-V00399
               MOVE 'X' TO WS-V00835
           END-IF.
           IF WS-V01148 = WS-V01290
               MOVE 'X' TO WS-V01225
           END-IF.
           PERFORM P00014.
           CALL 'PGM034' USING WS-V01708 WS-V01247.
           GO TO P00130.
       P00112.
           MOVE WS-V00304 TO WS-V01937.
           IF WS-V00847 = WS-V00553
               MOVE 'X' TO WS-V00573
           END-IF.
           IF WS-V00983 = WS-V01424
               MOVE 'X' TO WS-V00626
           END-IF.
           IF WS-V00546 = WS-V01006
               MOVE 'X' TO WS-V00439
           END-IF.
           PERFORM P00127.
           CALL 'PGM023' USING WS-V01226 WS-V00963.
           GO TO P00061.
       P00113.
           MOVE WS-V00693 TO WS-V00360.
           IF WS-V01240 = WS-V01554
               MOVE 'X' TO WS-V00371
           END-IF.
           IF WS-V01513 = WS-V01802
               MOVE 'X' TO WS-V01188
           END-IF.
           IF WS-V01421 = WS-V00923
               MOVE 'X' TO WS-V01095
           END-IF.
           PERFORM P00038.
           CALL 'PGM003' USING WS-V01032 WS-V00667.
           GO TO P00135.
       P00114.
           MOVE WS-V01413 TO WS-V00276.
           IF WS-V01321 = WS-V01559
               MOVE 'X' TO WS-V01662
           END-IF.
           IF WS-V01825 = WS-V00436
               MOVE 'X' TO WS-V00645
           END-IF.
           IF WS-V01274 = WS-V01011
               MOVE 'X' TO WS-V00983
           END-IF.
           PERFORM P00084.
           CALL 'PGM007' USING WS-V00261 WS-V01817.
           GO TO P00035.
       P00115.
           MOVE WS-V01430 TO WS-V00525.
           IF WS-V00460 = WS-V00180
               MOVE 'X' TO WS-V01301
           END-IF.
           IF WS-V01103 = WS-V01697
               MOVE 'X' TO WS-V01439
           END-IF.
           IF WS-V00102 = WS-V01153
               MOVE 'X' TO WS-V00352
           END-IF.
           PERFORM P00029.
           CALL 'PGM014' USING WS-V01153 WS-V00408.
           GO TO P00128.
       P00116.
           MOVE WS-V01162 TO WS-V01351.
           IF WS-V01811 = WS-V00630
               MOVE 'X' TO WS-V00864
           END-IF.
           IF WS-V00671 = WS-V00008
               MOVE 'X' TO WS-V01584
           END-IF.
           IF WS-V00041 = WS-V01682
               MOVE 'X' TO WS-V00625
           END-IF.
           PERFORM P00056.
           CALL 'PGM005' USING WS-V01521 WS-V00459.
           GO TO P00071.
       P00117.
           MOVE WS-V01394 TO WS-V01281.
           IF WS-V01763 = WS-V00698
               MOVE 'X' TO WS-V00551
           END-IF.
           IF WS-V01231 = WS-V01472
               MOVE 'X' TO WS-V01061
           END-IF.
           IF WS-V00776 = WS-V00047
               MOVE 'X' TO WS-V00249
           END-IF.
           PERFORM P00084.
           CALL 'PGM022' USING WS-V00285 WS-V00232.
           GO TO P00064.
       P00118.
           MOVE WS-V01840 TO WS-V01576.
           IF WS-V00293 = WS-V01395
               MOVE 'X' TO WS-V01175
           END-IF.
           IF WS-V00084 = WS-V00710
               MOVE 'X' TO WS-V00158
           END-IF.
           IF WS-V00188 = WS-V01484
               MOVE 'X' TO WS-V00211
           END-IF.
           PERFORM P00076.
           CALL 'PGM020' USING WS-V00509 WS-V00551.
           GO TO P00135.
       P00119.
           MOVE WS-V00101 TO WS-V00740.
           IF WS-V00063 = WS-V00160
               MOVE 'X' TO WS-V00284
           END-IF.
           IF WS-V01895 = WS-V00817
               MOVE 'X' TO WS-V00761
           END-IF.
           IF WS-V01912 = WS-V01474
               MOVE 'X' TO WS-V01307
           END-IF.
           PERFORM P00061.
           CALL 'PGM006' USING WS-V01391 WS-V00673.
           GO TO P00070.
       P00120.
           MOVE WS-V00016 TO WS-V01055.
           IF WS-V01819 = WS-V00659
               MOVE 'X' TO WS-V01963
           END-IF.
           IF WS-V01942 = WS-V00229
               MOVE 'X' TO WS-V00721
           END-IF.
           IF WS-V01890 = WS-V01645
               MOVE 'X' TO WS-V01619
           END-IF.
           PERFORM P00032.
           CALL 'PGM038' USING WS-V01898 WS-V01782.
           GO TO P00069.
       P00121.
           MOVE WS-V00829 TO WS-V00186.
           IF WS-V01390 = WS-V01180
               MOVE 'X' TO WS-V01271
           END-IF.
           IF WS-V01485 = WS-V01080
               MOVE 'X' TO WS-V00973
           END-IF.
           IF WS-V01155 = WS-V00857
               MOVE 'X' TO WS-V01097
           END-IF.
           PERFORM P00100.
           CALL 'PGM019' USING WS-V01839 WS-V00449.
           GO TO P00077.
       P00122.
           MOVE WS-V01124 TO WS-V00272.
           IF WS-V00110 = WS-V01228
               MOVE 'X' TO WS-V01041
           END-IF.
           IF WS-V00225 = WS-V00358
               MOVE 'X' TO WS-V00492
           END-IF.
           IF WS-V00440 = WS-V01837
               MOVE 'X' TO WS-V00890
           END-IF.
           PERFORM P00070.
           CALL 'PGM034' USING WS-V00040 WS-V00512.
           GO TO P00137.
       P00123.
           MOVE WS-V00554 TO WS-V01935.
           IF WS-V01085 = WS-V00536
               MOVE 'X' TO WS-V00969
           END-IF.
           IF WS-V00258 = WS-V00825
               MOVE 'X' TO WS-V01451
           END-IF.
           IF WS-V00212 = WS-V01525
               MOVE 'X' TO WS-V00764
           END-IF.
           PERFORM P00017.
           CALL 'PGM041' USING WS-V01114 WS-V00743.
           GO TO P00139.
       P00124.
           MOVE WS-V01137 TO WS-V01728.
           IF WS-V01647 = WS-V01481
               MOVE 'X' TO WS-V01039
           END-IF.
           IF WS-V01402 = WS-V01189
               MOVE 'X' TO WS-V00062
           END-IF.
           IF WS-V01267 = WS-V00631
               MOVE 'X' TO WS-V00912
           END-IF.
           PERFORM P00033.
           CALL 'PGM009' USING WS-V00152 WS-V01870.
           GO TO P00148.
       P00125.
           MOVE WS-V00290 TO WS-V01385.
           IF WS-V01800 = WS-V01693
               MOVE 'X' TO WS-V00442
           END-IF.
           IF WS-V00991 = WS-V01723
               MOVE 'X' TO WS-V01640
           END-IF.
           IF WS-V01737 = WS-V01571
               MOVE 'X' TO WS-V00687
           END-IF.
           PERFORM P00093.
           CALL 'PGM018' USING WS-V00327 WS-V00318.
           GO TO P00097.
       P00126.
           MOVE WS-V01706 TO WS-V00900.
           IF WS-V00830 = WS-V00241
               MOVE 'X' TO WS-V01230
           END-IF.
           IF WS-V00297 = WS-V00552
               MOVE 'X' TO WS-V00604
           END-IF.
           IF WS-V01365 = WS-V01406
               MOVE 'X' TO WS-V01637
           END-IF.
           PERFORM P00002.
           CALL 'PGM034' USING WS-V01950 WS-V00019.
           GO TO P00033.
       P00127.
           MOVE WS-V00777 TO WS-V01529.
           IF WS-V01150 = WS-V01929
               MOVE 'X' TO WS-V01807
           END-IF.
           IF WS-V00207 = WS-V00941
               MOVE 'X' TO WS-V00062
           END-IF.
           IF WS-V01595 = WS-V00884
               MOVE 'X' TO WS-V01225
           END-IF.
           PERFORM P00108.
           CALL 'PGM017' USING WS-V01911 WS-V00758.
           GO TO P00104.
       P00128.
           MOVE WS-V00831 TO WS-V01240.
           IF WS-V00946 = WS-V00109
               MOVE 'X' TO WS-V00203
           END-IF.
           IF WS-V00964 = WS-V01595
               MOVE 'X' TO WS-V00076
           END-IF.
           IF WS-V01323 = WS-V01442
               MOVE 'X' TO WS-V01428
           END-IF.
           PERFORM P00000.
           CALL 'PGM002' USING WS-V01703 WS-V00227.
           GO TO P00035.
       P00129.
           MOVE WS-V01086 TO WS-V01040.
           IF WS-V01563 = WS-V00729
               MOVE 'X' TO WS-V01128
           END-IF.
           IF WS-V00554 = WS-V01603
               MOVE 'X' TO WS-V01163
           END-IF.
           IF WS-V01861 = WS-V01983
               MOVE 'X' TO WS-V01341
           END-IF.
           PERFORM P00091.
           CALL 'PGM030' USING WS-V01678 WS-V01428.
           GO TO P00062.
       P00130.
           MOVE WS-V01899 TO WS-V01655.
           IF WS-V01273 = WS-V00491
               MOVE 'X' TO WS-V00216
           END-IF.
           IF WS-V01151 = WS-V01949
               MOVE 'X' TO WS-V00732
           END-IF.
           IF WS-V01785 = WS-V00324
               MOVE 'X' TO WS-V00238
           END-IF.
           PERFORM P00010.
           CALL 'PGM045' USING WS-V00642 WS-V00865.
           GO TO P00088.
       P00131.
           MOVE WS-V00519 TO WS-V01346.
           IF WS-V01281 = WS-V01839
               MOVE 'X' TO WS-V01581
           END-IF.
           IF WS-V01875 = WS-V00114
               MOVE 'X' TO WS-V01263
           END-IF.
           IF WS-V00890 = WS-V00849
               MOVE 'X' TO WS-V00770
           END-IF.
           PERFORM P00091.
           CALL 'PGM018' USING WS-V01544 WS-V01671.
           GO TO P00087.
       P00132.
           MOVE WS-V00903 TO WS-V01634.
           IF WS-V01432 = WS-V00487
               MOVE 'X' TO WS-V01300
           END-IF.
           IF WS-V01248 = WS-V01062
               MOVE 'X' TO WS-V00295
           END-IF.
           IF WS-V00114 = WS-V00699
               MOVE 'X' TO WS-V01378
           END-IF.
           PERFORM P00029.
           CALL 'PGM032' USING WS-V00352 WS-V01112.
           GO TO P00124.
       P00133.
           MOVE WS-V01831 TO WS-V00698.
           IF WS-V01551 = WS-V01454
               MOVE 'X' TO WS-V00248
           END-IF.
           IF WS-V01997 = WS-V01193
               MOVE 'X' TO WS-V00044
           END-IF.
           IF WS-V00983 = WS-V01837
               MOVE 'X' TO WS-V00428
           END-IF.
           PERFORM P00098.
           CALL 'PGM040' USING WS-V01707 WS-V01951.
           GO TO P00044.
       P00134.
           MOVE WS-V00813 TO WS-V01467.
           IF WS-V00466 = WS-V00204
               MOVE 'X' TO WS-V00508
           END-IF.
           IF WS-V00687 = WS-V01996
               MOVE 'X' TO WS-V01986
           END-IF.
           IF WS-V00673 = WS-V01344
               MOVE 'X' TO WS-V00502
           END-IF.
           PERFORM P00118.
           CALL 'PGM047' USING WS-V00964 WS-V00756.
           GO TO P00126.
       P00135.
           MOVE WS-V01334 TO WS-V01583.
           IF WS-V01358 = WS-V01481
               MOVE 'X' TO WS-V01918
           END-IF.
           IF WS-V00396 = WS-V00884
               MOVE 'X' TO WS-V00902
           END-IF.
           IF WS-V00816 = WS-V01110
               MOVE 'X' TO WS-V00246
           END-IF.
           PERFORM P00146.
           CALL 'PGM031' USING WS-V01898 WS-V00545.
           GO TO P00032.
       P00136.
           MOVE WS-V00306 TO WS-V00024.
           IF WS-V00770 = WS-V00849
               MOVE 'X' TO WS-V00223
           END-IF.
           IF WS-V01636 = WS-V00053
               MOVE 'X' TO WS-V01336
           END-IF.
           IF WS-V00152 = WS-V01927
               MOVE 'X' TO WS-V00374
           END-IF.
           PERFORM P00117.
           CALL 'PGM049' USING WS-V00772 WS-V01366.
           GO TO P00128.
       P00137.
           MOVE WS-V01634 TO WS-V01669.
           IF WS-V00590 = WS-V01879
               MOVE 'X' TO WS-V00318
           END-IF.
           IF WS-V00315 = WS-V01990
               MOVE 'X' TO WS-V01074
           END-IF.
           IF WS-V01691 = WS-V00216
               MOVE 'X' TO WS-V01939
           END-IF.
           PERFORM P00065.
           CALL 'PGM001' USING WS-V00951 WS-V00812.
           GO TO P00058.
       P00138.
           MOVE WS-V01101 TO WS-V01424.
           IF WS-V00800 = WS-V00010
               MOVE 'X' TO WS-V01114
           END-IF.
           IF WS-V01646 = WS-V00510
               MOVE 'X' TO WS-V00866
           END-IF.
           IF WS-V01858 = WS-V00325
               MOVE 'X' TO WS-V01356
           END-IF.
           PERFORM P00045.
           CALL 'PGM021' USING WS-V01356 WS-V00489.
           GO TO P00019.
       P00139.
           MOVE WS-V01587 TO WS-V01098.
           IF WS-V01915 = WS-V01142
               MOVE 'X' TO WS-V01966
           END-IF.
           IF WS-V00329 = WS-V00359
               MOVE 'X' TO WS-V00769
           END-IF.
           IF WS-V01198 = WS-V00044
               MOVE 'X' TO WS-V01050
           END-IF.
           PERFORM P00055.
           CALL 'PGM027' USING WS-V00482 WS-V01626.
           GO TO P00010.
       P00140.
           MOVE WS-V01916 TO WS-V01056.
           IF WS-V01484 = WS-V00389
               MOVE 'X' TO WS-V01434
           END-IF.
           IF WS-V01032 = WS-V01414
               MOVE 'X' TO WS-V01253
           END-IF.
           IF WS-V01338 = WS-V01099
               MOVE 'X' TO WS-V00158
           END-IF.
           PERFORM P00063.
           CALL 'PGM025' USING WS-V01596 WS-V00952.
           GO TO P00030.
       P00141.
           MOVE WS-V01161 TO WS-V01318.
           IF WS-V00099 = WS-V00792
               MOVE 'X' TO WS-V00183
           END-IF.
           IF WS-V01146 = WS-V00193
               MOVE 'X' TO WS-V01313
           END-IF.
           IF WS-V01669 = WS-V00980
               MOVE 'X' TO WS-V00092
           END-IF.
           PERFORM P00132.
           CALL 'PGM015' USING WS-V01592 WS-V00024.
           GO TO P00005.
       P00142.
           MOVE WS-V01956 TO WS-V01759.
           IF WS-V00638 = WS-V00955
               MOVE 'X' TO WS-V00569
           END-IF.
           IF WS-V01480 = WS-V00851
               MOVE 'X' TO WS-V00341
           END-IF.
           IF WS-V01218 = WS-V00272
               MOVE 'X' TO WS-V01150
           END-IF.
           PERFORM P00081.
           CALL 'PGM049' USING WS-V01095 WS-V01303.
           GO TO P00114.
       P00143.
           MOVE WS-V01027 TO WS-V01644.
           IF WS-V00855 = WS-V01134
               MOVE 'X' TO WS-V00343
           END-IF.
           IF WS-V01431 = WS-V00809
               MOVE 'X' TO WS-V01430
           END-IF.
           IF WS-V00796 = WS-V01655
               MOVE 'X' TO WS-V00411
           END-IF.
           PERFORM P00126.
           CALL 'PGM017' USING WS-V00737 WS-V01900.
           GO TO P00038.
       P00144.
           MOVE WS-V00531 TO WS-V01160.
           IF WS-V00572 = WS-V01730
               MOVE 'X' TO WS-V00358
           END-IF.
           IF WS-V01598 = WS-V01476
               MOVE 'X' TO WS-V01273
           END-IF.
           IF WS-V00171 = WS-V01497
               MOVE 'X' TO WS-V00738
           END-IF.
           PERFORM P00086.
           CALL 'PGM009' USING WS-V00529 WS-V00522.
           GO TO P00064.
       P00145.
           MOVE WS-V00715 TO WS-V00786.
           IF WS-V00571 = WS-V01158
               MOVE 'X' TO WS-V00957
           END-IF.
           IF WS-V00027 = WS-V00305
               MOVE 'X' TO WS-V01953
           END-IF.
           IF WS-V00266 = WS-V01963
               MOVE 'X' TO WS-V00517
           END-IF.
           PERFORM P00057.
           CALL 'PGM012' USING WS-V00144 WS-V01641.
           GO TO P00148.
       P00146.
           MOVE WS-V01100 TO WS-V01266.
           IF WS-V00406 = WS-V01112
               MOVE 'X' TO WS-V00879
           END-IF.
           IF WS-V01466 = WS-V01785
               MOVE 'X' TO WS-V00491
           END-IF.
           IF WS-V01182 = WS-V00285
               MOVE 'X' TO WS-V01134
           END-IF.
           PERFORM P00117.
           CALL 'PGM025' USING WS-V01457 WS-V00401.
           GO TO P00021.
       P00147.
           MOVE WS-V01280 TO WS-V01914.
           IF WS-V00158 = WS-V00313
               MOVE 'X' TO WS-V01610
           END-IF.
           IF WS-V01366 = WS-V00117
               MOVE 'X' TO WS-V00061
           END-IF.
           IF WS-V01528 = WS-V00830
               MOVE 'X' TO WS-V00783
           END-IF.
           PERFORM P00106.
           CALL 'PGM043' USING WS-V00281 WS-V01210.
           GO TO P00033.
       P00148.
           MOVE WS-V01377 TO WS-V01102.
           IF WS-V01118 = WS-V00151
               MOVE 'X' TO WS-V01899
           END-IF.
           IF WS-V00494 = WS-V01740
               MOVE 'X' TO WS-V00781
           END-IF.
           IF WS-V00285 = WS-V00585
               MOVE 'X' TO WS-V00414
           END-IF.
           PERFORM P00101.
           CALL 'PGM022' USING WS-V01534 WS-V01717.
           GO TO P00045.
       P00149.
           MOVE WS-V00461 TO WS-V00609.
           IF WS-V01452 = WS-V00294
               MOVE 'X' TO WS-V00712
           END-IF.
           IF WS-V01007 = WS-V01096
               MOVE 'X' TO WS-V00597
           END-IF.
           IF WS-V00181 = WS-V01053
               MOVE 'X' TO WS-V01694
           END-IF.
           PERFORM P00076.
           CALL 'PGM013' USING WS-V01444 WS-V00949.
           GO TO P00005.
       FINAL-PARA.
           GOBACK.
