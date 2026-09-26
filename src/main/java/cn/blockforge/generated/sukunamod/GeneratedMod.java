package cn.blockforge.generated.sukunamod;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.particles.ParticleType;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
@Mod(GeneratedMod.MOD_ID)
public final class GeneratedMod {
    public static final String MOD_ID = "sukunamod";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MOD_ID);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MOD_ID);
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, MOD_ID);
    public static final RegistryObject<SoundEvent> GRID_SLASH_SOUND = registerSound("grid_slash");
    /** 宿傩斩击音效：仅普通解与后撤解施放时播放（连续解已按要求不再使用）。 */
    public static final RegistryObject<SoundEvent> SUKUNA_SLASH_SOUND = registerSound("sukuna_slash_1");
    /** 领域前摇（刚开始读条）时播放一次的"伏魔御厨子前摇"。 */
    public static final RegistryObject<SoundEvent> DOMAIN_CHARGE_SOUND = registerSound("domain_charge");
    /** 神龛展开（领域开启）时播放一次的"伏魔御厨子"。 */
    public static final RegistryObject<SoundEvent> DOMAIN_EXPAND_SOUND = registerSound("domain_expand");
    /** 世界斩脱手发射瞬间播放的"终末斩"音效。 */
    public static final RegistryObject<SoundEvent> WORLD_SLASH_SOUND = registerSound("world_slash");
    public static final RegistryObject<SimpleParticleType> KITCHEN_FLAME = PARTICLES.register(
            "kitchen_flame", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> REVERSE_FLAME = PARTICLES.register(
            "reverse_flame", () -> new SimpleParticleType(true));
    /** 灶·开重置准备：固定占据 2×2 方块的大型火焰粒子。 */
    public static final RegistryObject<SimpleParticleType> KITCHEN_FLAME_2X2 = PARTICLES.register(
            "kitchen_flame_2x2", () -> new SimpleParticleType(true));
    /** 爆炸火柱：从柱底冲天而起、到最高点用 0.2 秒淡出的大型火焰。 */
    public static final RegistryObject<SimpleParticleType> KITCHEN_FLAME_PILLAR = PARTICLES.register(
            "kitchen_flame_pillar", () -> new SimpleParticleType(true));
    /** 起爆前 2 秒引信里的十字闪光：前四发 0.15 秒放大缩小，两张贴图交替。 */
    public static final RegistryObject<SimpleParticleType> FLASH_CROSS_1 = PARTICLES.register(
            "flash_cross_1", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> FLASH_CROSS_2 = PARTICLES.register(
            "flash_cross_2", () -> new SimpleParticleType(true));
    /** 第五波闪光：第四发后 0.2 秒出现，用 0.2 秒从小平滑放大到最大后消失。 */
    public static final RegistryObject<SimpleParticleType> FLASH_CROSS_5 = PARTICLES.register(
            "flash_cross_5", () -> new SimpleParticleType(true));
    /** 打击帧动画（打击1~4）：0.15 秒播完一轮的白色冲击环，连打/追击拳区随机出现。 */
    public static final RegistryObject<SimpleParticleType> STRIKE_FRAME = PARTICLES.register(
            "strike_frame", () -> new SimpleParticleType(true));
    /** 重击用的放大两倍打击帧动画。 */
    public static final RegistryObject<SimpleParticleType> STRIKE_FRAME_BIG = PARTICLES.register(
            "strike_frame_big", () -> new SimpleParticleType(true));
    /** 打击命中帧动画（打击命中1~3）：随机贴在受击实体身上。 */
    public static final RegistryObject<SimpleParticleType> STRIKE_HIT_FRAME = PARTICLES.register(
            "strike_hit_frame", () -> new SimpleParticleType(true));

    private static RegistryObject<SoundEvent> registerSound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(
                new net.minecraft.resources.ResourceLocation(MOD_ID, name)));
    }

    public static final RegistryObject<Item> SUKUNA_MARK = ITEMS.register("sukuna_mark",
            () -> new SukunaMarkItem(new Item.Properties().stacksTo(1)));
    /** 灶·开准备资源；不加入创造栏，避免成为可直接使用的成品技能物品。 */
    public static final RegistryObject<Item> FLAME_ARROW = ITEMS.register("flame_arrow",
            () -> new Item(new Item.Properties().stacksTo(1)));
    /** Internal display stack used by the domain's shrine entity. */
    public static final RegistryObject<Item> SHRINE = ITEMS.register("shrine",
            () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<MobEffect> SLICING_EFFECT = MOB_EFFECTS.register("slicing",
            SlicingEffect::new);
    public static final RegistryObject<EntityType<DismantleProjectile>> DISMANTLE_PROJECTILE =
            ENTITY_TYPES.register("dismantle", () -> EntityType.Builder
                    .<DismantleProjectile>of(DismantleProjectile::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(64).updateInterval(1)
                    .build(MOD_ID + ":dismantle"));
    /**
     * 隐形破坏斩击：普通解/后撤解/连续解随贴图额外发射的高速实体，无任何渲染，
     * 撞到方块才按贴图对齐的位置切出 10宽1厚10深 的破坏框。
     */
    public static final RegistryObject<EntityType<TrenchSlashEntity>> TRENCH_SLASH =
            ENTITY_TYPES.register("trench_slash", () -> EntityType.Builder
                    .<TrenchSlashEntity>of(TrenchSlashEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(32).updateInterval(5)
                    .build(MOD_ID + ":trench_slash"));
    public static final RegistryObject<EntityType<ShrineEntity>> SHRINE_ENTITY =
            ENTITY_TYPES.register("shrine", () -> EntityType.Builder
                    .<ShrineEntity>of(ShrineEntity::new, MobCategory.MISC)
                    .sized(9.0F, 9.0F).clientTrackingRange(128).updateInterval(1)
                    .build(MOD_ID + ":shrine"));
    public static final RegistryObject<EntityType<FlameArrowEntity>> FLAME_ARROW_ENTITY =
            ENTITY_TYPES.register("flame_arrow", () -> EntityType.Builder
                    .<FlameArrowEntity>of(FlameArrowEntity::new, MobCategory.MISC)
                    .sized(0.8F, 0.8F).clientTrackingRange(64).updateInterval(1)
                    .build(MOD_ID + ":flame_arrow"));

    public GeneratedMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
        ENTITY_TYPES.register(bus);
        MOB_EFFECTS.register(bus);
        SOUNDS.register(bus);
        PARTICLES.register(bus);
        bus.addListener(GeneratedMod::addCreativeTab);
        JjkoaNetwork.init();
    }

    public static void addCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.INGREDIENTS)) {
            event.accept(SUKUNA_MARK);
            // The shrine is a domain-only visual anchor, not an inventory item.
        }
    }
}
