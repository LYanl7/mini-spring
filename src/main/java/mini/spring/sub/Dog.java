package mini.spring.sub;

import mini.spring.Autowired;
import mini.spring.Component;
import mini.spring.PostConstruct;

@Component
public class Dog {
    @Autowired
    private Cat cat;

    @PostConstruct
    public void init() {
        System.out.println("Dog init" + cat);
    }
}
