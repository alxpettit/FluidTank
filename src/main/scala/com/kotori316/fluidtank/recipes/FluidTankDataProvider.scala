package com.kotori316.fluidtank.recipes

import java.io.IOException

import com.google.gson.GsonBuilder
import com.kotori316.fluidtank.{FluidTank, ModObjects}
import net.minecraft.advancements.criterion._
import net.minecraft.block.Blocks
import net.minecraft.data._
import net.minecraft.item.crafting.Ingredient
import net.minecraft.item.{Item, Items}
import net.minecraft.tags.{ItemTags, Tag}
import net.minecraft.util.ResourceLocation
import net.minecraftforge.common.Tags
import net.minecraftforge.common.crafting.conditions.NotCondition
import net.minecraftforge.fml.event.lifecycle.GatherDataEvent
import net.minecraftforge.registries.ForgeRegistries

object FluidTankDataProvider {
  def gatherData(event: GatherDataEvent): Unit = {
    if (event.includeServer()) {
      event.getGenerator.addProvider(new AdvancementProvider(event.getGenerator))
      event.getGenerator.addProvider(new RecipeProvider(event.getGenerator))
    }
  }

  private[this] final val ID = (s: String) => new ResourceLocation(FluidTank.modID, s)

  class AdvancementProvider(generatorIn: DataGenerator) extends IDataProvider {
    override def act(cache: DirectoryCache): Unit = {
      val path = generatorIn.getOutputFolder
      val GSON = (new GsonBuilder).setPrettyPrinting().create

      val TANK_WOOD = AdvancementSerializeHelper(ID("tank_wood"))
        .addCriterion("has_bucket", FilledBucketTrigger.Instance.forItem(ItemPredicate.Builder.create().item(Items.WATER_BUCKET).build()))
        .addItemCriterion(Tags.Items.GLASS)
      val TANK_WOOD_EASY = AdvancementSerializeHelper(ID("tank_wood_easy"))
        .addCriterion("has_bucket", FilledBucketTrigger.Instance.forItem(ItemPredicate.Builder.create().item(Items.WATER_BUCKET).build()))
        .addItemCriterion(Tags.Items.GLASS)
      val TANKS = ModObjects.blockTanks.collect { case b if b.tier.hasOreRecipe => b.tier }
        .map(tier => AdvancementSerializeHelper(ID("tank_" + tier.toString.toLowerCase)).addItemCriterion(new Tag[Item](new ResourceLocation(tier.oreName))))
      val CAT = AdvancementSerializeHelper(ID("chest_as_tank"))
        .addItemCriterion(ForgeRegistries.ITEMS.getValue(new ResourceLocation("fluidtank:tank_wood")))
        .addCriterion("has_lots_of_items", new InventoryChangeTrigger.Instance(MinMaxBounds.IntBound.atLeast(10),
          MinMaxBounds.IntBound.UNBOUNDED, MinMaxBounds.IntBound.UNBOUNDED, Array(ItemPredicate.Builder.create().item(Items.WATER_BUCKET).build())))
      val PIPE = AdvancementSerializeHelper(ID("pipe"))
        .addItemCriterion(Tags.Items.ENDER_PEARLS)
        .addItemCriterion(ForgeRegistries.ITEMS.getValue(new ResourceLocation("fluidtank:tank_wood")))
      val recipeAdvancements = PIPE :: CAT :: TANK_WOOD :: TANK_WOOD_EASY :: TANKS

      for (recipe <- recipeAdvancements) {
        val out = path.resolve(s"data/${recipe.location.getNamespace}/advancements/${recipe.location.getPath}.json")
        try {
          IDataProvider.save(GSON, cache, recipe.build, out)
        } catch {
          case e: IOException => FluidTank.LOGGER.error(s"Failed to save recipe ${recipe.location}.", e)
        }
      }
    }

    override def getName = "Advancement of FluidTank"
  }

  class RecipeProvider(generatorIn: DataGenerator) extends IDataProvider {
    override def act(cache: DirectoryCache): Unit = {
      val path = generatorIn.getOutputFolder
      val GSON = (new GsonBuilder).setPrettyPrinting().create

      val woodLocation = ID("tank_wood")
      val WOOD = RecipeSerializeHelper(getConsumeValue(
        ShapedRecipeBuilder.shapedRecipe(ForgeRegistries.ITEMS.getValue(woodLocation))
          .key('x', Tags.Items.GLASS).key('p', ItemTags.LOGS)
          .patternLine("x x")
          .patternLine("xpx")
          .patternLine("xxx")))
        .addCondition(ConfigCondition.getInstance())
        .addCondition(new NotCondition(EasyCondition.getInstance()))
      val EASY_WOOD = RecipeSerializeHelper(
        getConsumeValue(
          ShapedRecipeBuilder.shapedRecipe(ForgeRegistries.ITEMS.getValue(woodLocation))
            .key('x', Tags.Items.GLASS).key('p', ItemTags.PLANKS)
            .patternLine("p p")
            .patternLine("p p")
            .patternLine("xpx")), saveName = ID("tank_wood_easy"))
        .addCondition(ConfigCondition.getInstance())
        .addCondition(EasyCondition.getInstance())
      val CAT = RecipeSerializeHelper(
        getConsumeValue(
          ShapedRecipeBuilder.shapedRecipe(ModObjects.blockCat)
            .key('x', ForgeRegistries.ITEMS.getValue(woodLocation))
            .key('p', Ingredient.merge(java.util.Arrays.asList(Ingredient.fromTag(Tags.Items.CHESTS), Ingredient.fromItems(Blocks.BARREL))))
            .patternLine("x x")
            .patternLine("xpx")
            .patternLine("xxx")))
      val PIPE = RecipeSerializeHelper(
        getConsumeValue(
          ShapedRecipeBuilder.shapedRecipe(ModObjects.blockPipe)
            .key('t', ForgeRegistries.ITEMS.getValue(woodLocation))
            .key('g', Tags.Items.GLASS)
            .key('e', Tags.Items.ENDER_PEARLS)
            .patternLine("gtg")
            .patternLine(" e ")
            .patternLine("gtg")))
      val TANKS = ModObjects.blockTanks.collect { case b if b.tier.hasOreRecipe => b.tier }
        .map(tier => RecipeSerializeHelper(new TierRecipe.FinishedRecipe(ID("tank_" + tier.toString.toLowerCase), tier))
          .addTagCondition(new Tag[Item](new ResourceLocation(tier.oreName)))
          .addCondition(ConfigCondition.getInstance()))

      val recipes = PIPE :: CAT :: WOOD :: EASY_WOOD :: TANKS

      for (recipe <- recipes) {
        val out = path.resolve(s"data/${recipe.location.getNamespace}/recipes/${recipe.location.getPath}.json")
        try {
          IDataProvider.save(GSON, cache, recipe.build, out)
        } catch {
          case e: IOException => FluidTank.LOGGER.error(s"Failed to save recipe ${recipe.location}.", e)
        }
      }
    }

    override def getName = "Recipe of FluidTank"

    private def getConsumeValue(c: ShapedRecipeBuilder): IFinishedRecipe = {
      c.addCriterion("dummy", new RecipeUnlockedTrigger.Instance(ID("dummy")))
      var t: IFinishedRecipe = null
      c.build(p => t = p)
      t
    }
  }

}