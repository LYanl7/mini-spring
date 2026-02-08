package mini.spring.sub;

import mini.spring.IoC.Autowired;
import mini.spring.IoC.Component;
import mini.spring.IoC.PostConstruct;

@Component
public class Cat {
    @Autowired
    private Dog dog;

    @PostConstruct
    public void init() {
        System.out.println("Cat init" + dog);
    }
}
