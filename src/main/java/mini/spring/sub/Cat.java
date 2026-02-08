package mini.spring.sub;

import mini.spring.Autowired;
import mini.spring.Component;
import mini.spring.PostConstruct;

@Component
public class Cat {
    @Autowired
    private Dog dog;

    @PostConstruct
    public void init() {
        System.out.println("Cat init" + dog);
    }
}
