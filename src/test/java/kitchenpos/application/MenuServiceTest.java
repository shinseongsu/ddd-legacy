package kitchenpos.application;

import kitchenpos.domain.Menu;
import kitchenpos.domain.MenuGroup;
import kitchenpos.domain.MenuGroupRepository;
import kitchenpos.domain.MenuProduct;
import kitchenpos.domain.MenuRepository;
import kitchenpos.domain.Product;
import kitchenpos.domain.ProductRepository;
import kitchenpos.infra.PurgomalumClient;
import kitchenpos.util.MenuFactory;
import kitchenpos.util.MenuGroupFactory;
import kitchenpos.util.ProductFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DataJpaTest
@Transactional
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MenuServiceTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private MenuGroupRepository menuGroupRepository;

    private PurgomalumClient purgomalumClient;

    private MenuService menuService;

    @BeforeEach
    void setUp() {
        purgomalumClient = new FakePurgomalumClient(false);
        menuService = new MenuService(menuRepository, menuGroupRepository, productRepository, purgomalumClient);
        MenuGroup request = MenuGroupFactory.createMenuGroup(UUID.randomUUID(), "test group");
        menuGroupRepository.save(request);
    }

    @DisplayName("메뉴를 생성할 수 있다.")
    @Test
    void create_with_valid_attribute() {
        final Product product = productRepository.save(ProductFactory.createProduct("test1", 1000));
        final List<MenuProduct> menuProducts = MenuFactory.createMenuProducts(Collections.singletonList(product), 1);
        final MenuGroup menuGroup = findAnyMenuGroup();
        final Menu request = MenuFactory.createMenu(BigDecimal.valueOf(1000), "menu1", true, menuGroup, menuProducts);

        final Menu actual = menuService.create(request);

        assertThat(actual).isNotNull();
    }

    @DisplayName("메뉴의 그룹정보가 존재해야한다.")
    @Test
    void create_with_no_menu_group() {
        final Product product = productRepository.save(ProductFactory.createProduct("test1", 1000));
        final List<MenuProduct> menuProducts = MenuFactory.createMenuProducts(Collections.singletonList(product), 1);
        final String notFoundUUID = "06fe3514-a8a6-48ed-85e6-e7296d0e1000";
        final MenuGroup menuGroup = menuGroupRepository.getById(UUID.fromString(notFoundUUID));
        final Menu request = MenuFactory.createMenu(BigDecimal.valueOf(1000), "menu1", true, menuGroup, menuProducts);

        assertThatCode(() ->
                menuService.create(request)
        ).isInstanceOf(NoSuchElementException.class);
    }

    @DisplayName("메뉴 이름에 비속어가 들어가면 안된다.")
    @Test
    void create_with_not_allowed_name() {
        menuService = new MenuService(menuRepository, menuGroupRepository, productRepository, new FakePurgomalumClient(true));
        final Product product = productRepository.save(ProductFactory.createProduct("test1", 1000));
        final List<MenuProduct> menuProducts = MenuFactory.createMenuProducts(Collections.singletonList(product), 1);
        final MenuGroup menuGroup = findAnyMenuGroup();
        final String not_allowed_name = "비속어가 들어간 메뉴이름";
        final Menu request = MenuFactory.createMenu(BigDecimal.valueOf(1000), not_allowed_name, true, menuGroup, menuProducts);

        assertThatCode(() ->
                menuService.create(request)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("메뉴 이름이 존재해야한다.")
    @Test
    void create_with_null_name() {
        menuService = new MenuService(menuRepository, menuGroupRepository, productRepository, new FakePurgomalumClient(true));
        final Product product = productRepository.save(ProductFactory.createProduct("test1", 1000));
        final List<MenuProduct> menuProducts = MenuFactory.createMenuProducts(Collections.singletonList(product), 1);
        final MenuGroup menuGroup = findAnyMenuGroup();
        final String givenName = null;
        final Menu request = MenuFactory.createMenu(BigDecimal.valueOf(1000), givenName, true, menuGroup, menuProducts);

        assertThatCode(() ->
                menuService.create(request)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("메뉴 상품 목록정보가 비어있으면 안된다.")
    @Test
    void create_with_empty_product() {
        menuService = new MenuService(menuRepository, menuGroupRepository, productRepository, new FakePurgomalumClient(true));
        final List<MenuProduct> menuProducts = Collections.emptyList();
        final Menu request = MenuFactory.createMenu(BigDecimal.valueOf(1000), "menu1", true, findAnyMenuGroup(), menuProducts);

        assertThatCode(() ->
                menuService.create(request)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("메뉴의 가격 정보가 있어야한다.")
    @Test
    void create_with_null_price() {
        final Menu request = MenuFactory.createMenuWithPrice(null);

        assertThatCode(() ->
                menuService.create(request)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("메뉴의 가격은 0보다 작을 수 없다.")
    @ParameterizedTest
    @ValueSource(ints = {-100, -1000, -5000})
    void create_with_negative_price(int price) {
        final Product product = productRepository.save(ProductFactory.createProduct("test1", 1000));
        final List<MenuProduct> menuProducts = MenuFactory.createMenuProducts(Collections.singletonList(product), 1);
        final MenuGroup menuGroup = findAnyMenuGroup();
        final Menu request = MenuFactory.createMenu(BigDecimal.valueOf(price), "menu1", true, menuGroup, menuProducts);

        assertThatCode(() ->
                menuService.create(request)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("메뉴의 가격은 메뉴 상품들의 합보다 클 수 없다.")
    @Test
    void create_with_price_more_than_price_sum() {
        final Product product = productRepository.save(ProductFactory.createProduct("test1", 100));
        final List<MenuProduct> menuProducts = MenuFactory.createMenuProducts(Collections.singletonList(product), 1);
        final MenuGroup menuGroup = findAnyMenuGroup();
        final Menu request = MenuFactory.createMenu(BigDecimal.valueOf(2000), "menu1", true, menuGroup, menuProducts);

        assertThatCode(() ->
                menuService.create(request)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("메뉴 상품의 정보가 존재해야한다")
    @Test
    void create_with_not_exist_product() {
        final Product product = ProductFactory.createProduct("test1", 100);
        final List<MenuProduct> menuProducts = MenuFactory.createMenuProducts(Collections.singletonList(product), 1);
        final MenuGroup menuGroup = findAnyMenuGroup();
        final Menu request = MenuFactory.createMenu(BigDecimal.valueOf(2000), "menu1", true, menuGroup, menuProducts);

        assertThatCode(() ->
                menuService.create(request)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("메뉴 상품 정보가 존재해야한다. ")
    @ParameterizedTest
    @NullAndEmptySource
    void create_with_no_menu_product(List<MenuProduct> menuProducts) {
        final MenuGroup menuGroup = findAnyMenuGroup();
        final Menu request = MenuFactory.createMenu(BigDecimal.valueOf(1000), "menu1", true, menuGroup, menuProducts);

        assertThatCode(() ->
                menuService.create(request)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("메뉴 상품 갯수 해당하는 상품과 갯수가 일치해야한다.")
    @Test
    void create_with_different_product_size() {
        final Product product1 = productRepository.save(ProductFactory.createProduct("test1", 1000));
        final Product product2 = ProductFactory.createProduct("test2", 1000);
        final List<MenuProduct> menuProducts = MenuFactory.createMenuProducts(Arrays.asList(product1, product2), 1);
        final MenuGroup menuGroup = findAnyMenuGroup();
        final Menu request = MenuFactory.createMenu(BigDecimal.valueOf(2000), "menu1", true, menuGroup, menuProducts);

        assertThatCode(() ->
                menuService.create(request)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("메뉴 상품의 수량은 0보다 작을 수 없다.")
    @ParameterizedTest
    @ValueSource(ints = {-1, -10, -100})
    void create_with_negative_menu_product_quantity(int quantity) {
        final Product product1 = productRepository.save(ProductFactory.createProduct("test1", 1000));
        final Product product2 = productRepository.save(ProductFactory.createProduct("test2", 1000));
        final List<MenuProduct> menuProducts = MenuFactory.createMenuProducts(Arrays.asList(product1, product2), quantity);
        final MenuGroup menuGroup = findAnyMenuGroup();
        final Menu request = MenuFactory.createMenu(BigDecimal.valueOf(2000), "menu1", true, menuGroup, menuProducts);

        assertThatCode(() ->
                menuService.create(request)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("메뉴의 가격을 변경할 수 있다.")
    @Test
    void change_price() {
        final Product product = productRepository.save(ProductFactory.createProduct("test1", 1000));
        final List<MenuProduct> menuProducts = MenuFactory.createMenuProducts(Collections.singletonList(product), 1);
        final Menu requestMenu = MenuFactory.createMenu(BigDecimal.valueOf(1000), "menu1", true, findAnyMenuGroup(), menuProducts);
        final Menu savedMenu = menuService.create(requestMenu);
        final int changePrice = 500;
        final Menu changeRequest = MenuFactory.createMenuWithPrice(BigDecimal.valueOf(changePrice));

        final Menu actual = menuService.changePrice(savedMenu.getId(), changeRequest);

        assertThat(actual.getPrice()).isEqualTo(BigDecimal.valueOf(changePrice));
    }

    @DisplayName("변경하려는 가격 정보가 존재해야한다.")
    @Test
    void change_price_with_null_price() {
        final Product product = productRepository.save(ProductFactory.createProduct("test1", 1000));
        final List<MenuProduct> menuProducts = MenuFactory.createMenuProducts(Collections.singletonList(product), 1);
        final Menu requestMenu = MenuFactory.createMenu(BigDecimal.valueOf(1000), "menu1", true, findAnyMenuGroup(), menuProducts);
        final Menu savedMenu = menuService.create(requestMenu);
        final Menu changeRequest = new Menu();
        changeRequest.setPrice(null);

        assertThatCode(() ->
                menuService.changePrice(savedMenu.getId(), changeRequest)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("변경하려는 가격은 0보다 작을 수 없다.")
    @ParameterizedTest
    @ValueSource(ints = {-100, -1000, -5000})
    void change_price_with_negative_price(int changePrice) {
        final Product product = productRepository.save(ProductFactory.createProduct("test1", 1000));
        final List<MenuProduct> menuProducts = MenuFactory.createMenuProducts(Collections.singletonList(product), 1);
        final Menu requestMenu = MenuFactory.createMenu(BigDecimal.valueOf(1000), "menu1", true, findAnyMenuGroup(), menuProducts);
        final Menu savedMenu = menuService.create(requestMenu);
        final Menu changeRequest = new Menu();
        changeRequest.setPrice(BigDecimal.valueOf(changePrice));

        assertThatCode(() ->
                menuService.changePrice(savedMenu.getId(), changeRequest)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("메뉴의 가격은 메뉴 상품들의 합보다 클 수 없다.")
    @ParameterizedTest
    @ValueSource(ints = {2000, 3000, 5000})
    void change_price_with_product_sum(int changePrice) {
        final int givenPrice = 1000;
        final Product product = productRepository.save(ProductFactory.createProduct("test1", givenPrice));
        final List<MenuProduct> menuProducts = MenuFactory.createMenuProducts(Collections.singletonList(product), 1);
        final Menu requestMenu = MenuFactory.createMenu(BigDecimal.valueOf(1000), "menu1", true, findAnyMenuGroup(), menuProducts);
        final Menu savedMenu = menuService.create(requestMenu);
        final Menu changeRequest = MenuFactory.createMenuWithPrice(BigDecimal.valueOf(changePrice));

        assertThatCode(() ->
                menuService.changePrice(savedMenu.getId(), changeRequest)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("메뉴 목록을 조회할 수 있다.")
    @Test
    void find_all() {
        final Product product = productRepository.save(ProductFactory.createProduct("test1", 1000));
        final List<MenuProduct> menuProducts = MenuFactory.createMenuProducts(Collections.singletonList(product), 1);
        final Menu request1 = MenuFactory.createMenu(BigDecimal.valueOf(1000), "menu1", true, findAnyMenuGroup(), menuProducts);
        final Menu request2 = MenuFactory.createMenu(BigDecimal.valueOf(1000), "menu2", true, findAnyMenuGroup(), menuProducts);
        final Menu menu1 = menuService.create(request1);
        final Menu menu2 = menuService.create(request2);

        final List<Menu> actual = menuService.findAll();

        assertThat(actual).containsAll(Arrays.asList(menu1, menu2));
    }

    @DisplayName("메뉴 전시여부를 활성화 할 수 있다.")
    @Test
    void display() {
        final Product product = productRepository.save(ProductFactory.createProduct("test1", 1000));
        final List<MenuProduct> menuProducts = MenuFactory.createMenuProducts(Collections.singletonList(product), 1);
        final Menu request1 = MenuFactory.createMenu(BigDecimal.valueOf(1000), "menu1", false, findAnyMenuGroup(), menuProducts);
        final Menu givenMenu = menuService.create(request1);

        final Menu actual = menuService.display(givenMenu.getId());

        assertThat(actual.isDisplayed()).isTrue();
    }

    @DisplayName("메뉴 전시 활성화시 메뉴 정보가 존재해야한다.")
    @Test
    void display_with_not_exist_menu() {
        final String notFoundUUID = "06fe3514-a8a6-48ed-85e6-e7296d0e1000";

        assertThatCode(() ->
                menuService.display(UUID.fromString(notFoundUUID))
        ).isInstanceOf(NoSuchElementException.class);
    }

    @DisplayName("메뉴의 가격은 메뉴 상품들의 합보다 클 수 없다.")
    @Test
    void display_with_price_more_than_price_sum() {
        final Product product = productRepository.save(ProductFactory.createProduct("test1", 1000));
        final List<MenuProduct> menuProducts = MenuFactory.createMenuProducts(Collections.singletonList(product), 1);
        final Menu request = new Menu();
        request.setId(UUID.randomUUID());
        request.setPrice(BigDecimal.valueOf(2000));
        request.setName("test1");
        request.setDisplayed(false);
        request.setMenuProducts(menuProducts);
        request.setMenuGroup(findAnyMenuGroup());
        Menu givenMenu = menuRepository.save(request);

        assertThatCode(() ->
                menuService.display(givenMenu.getId())
        ).isInstanceOf(IllegalStateException.class);
    }

    @DisplayName("메뉴 전시여부를 비활성화 할 수 있다.")
    @Test
    void hide() {
        final Product product = productRepository.save(ProductFactory.createProduct("test1", 1000));
        final List<MenuProduct> menuProducts = MenuFactory.createMenuProducts(Collections.singletonList(product), 1);
        final Menu request1 = MenuFactory.createMenu(BigDecimal.valueOf(1000), "menu1", true, findAnyMenuGroup(), menuProducts);
        final Menu givenMenu = menuService.create(request1);

        final Menu actual = menuService.hide(givenMenu.getId());

        assertThat(actual.isDisplayed()).isFalse();
    }

    @DisplayName("메뉴 전시 비활성화시 메뉴 정보가 존재해야한다.")
    @Test
    void hide_with_not_exist_menu() {
        final String notFoundUUID = "06fe3514-a8a6-48ed-85e6-e7296d0e1000";

        assertThatCode(() ->
                menuService.hide(UUID.fromString(notFoundUUID))
        ).isInstanceOf(NoSuchElementException.class);
    }

    private MenuGroup findAnyMenuGroup() {
        return menuGroupRepository.findAll()
                .stream()
                .findAny()
                .orElseThrow(EntityNotFoundException::new);
    }
}