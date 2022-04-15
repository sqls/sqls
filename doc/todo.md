
# open

## features

- connected / not connected state of the worksheet,
- separate main worksheet conn from the helper conn,
- detect and warn if current transaction holds locks,
- detect and warn if current transaction is open too long
- optionally auto rollback broken transactions on PostgreSQL and maybe others,
- add type size to the desc box,
- fix config file detection logic,
- builtin support for ssh tunnels,
- clojure starts slow (even though it's quite fast), add a splash screen in
  pure Java before clojure init,
- show resident set size memory stats in the status bar,
- show mem stats in the conn list window also.

## bugs

-   conn error on "test conn" does not fit the message box
-   mem stats should be visible in the new worksheet immediately if the app is
    running for more than ~3s

# done

- on the conn list box, an indicator if the worksheet is open
- memory stats should be aligned right

