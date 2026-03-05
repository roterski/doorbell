#### simple invokation with missing arguments triggers interactive mode:
````
bb examples/simple_form.clj
````
````
bb examples/person_form.clj
````

##### when all (including default) values are provided interactive mode is skipped:
````
bb examples/simple_form.clj name bar
````

````
bb examples/person_form.clj first-name Bob looks.eye-color blue age 30 "looks.tattoos?" true mood ok "do-you-know-them?" true
````

- attributes ending with `?` need to be quoted

- nested map keys are chained with `.` e.g. `looks.eye-color`
