package mini.spring.sub;

import mini.spring.IoC.Autowired;
import mini.spring.IoC.Component;
import mini.spring.IoC.PostConstruct;

@Component
public class Dog {
    @Autowired
    private Cat cat;

    @PostConstruct
    public void init() {
        System.out.println("Dog init" + cat);
    }
}
