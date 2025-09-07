package com.globalmarket;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.globalmarket.util.AntiDuplicationManager;
import com.globalmarket.util.OfflineTransactionManager;
import com.globalmarket.util.SimpleOfflineInventory;

public class MarketCommand implements TabExecutor {
    
    private final GlobalMarket plugin;
    
    public MarketCommand(GlobalMarket plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 处理控制台命令
        if (!(sender instanceof Player)) {
            if (args.length > 0 && "reload".equalsIgnoreCase(args[0])) {
                if (!sender.hasPermission("globalmarket.reload")) {
                    sender.sendMessage("你没有权限执行此命令!");
                    return true;
                }
                
                plugin.reloadPlugin();
                sender.sendMessage("GlobalMarket 插件已从控制台重新加载!");
                return true;
            } else {
                sender.sendMessage(ChatColor.RED + "该命令只能由玩家执行! (除了 /market reload)");
                return true;
            }
        }
        
        Player player = (Player) sender;
        
        if (args.length == 0) {
            showHelp(player);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "create":
                handleCreateCommand(player, args);
                break;
            case "listing":
                handleListingCommand(player);
                break;
            case "mail":
                handleMailboxCommand(player);
                break;
            case "send":
                handleSendCommand(player, args);
                break;
            case "search":
                handleSearchCommand(player, args);
                break;
            case "reload":
                handleReloadCommand(player);
                break;
            default:
                showHelp(player);
                break;
        }
        
        return true;
    }
    
    private void handleCreateCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "用法: /market create <总价> [数量]");
            return;
        }
        
        try {
            double totalPrice = Double.parseDouble(args[1]);
            if (totalPrice <= 0) {
                player.sendMessage(ChatColor.RED + "总价必须大于0!");
                return;
            }
            
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item == null || item.getType().isAir()) {
                player.sendMessage(ChatColor.RED + "你必须手持一个物品来出售!");
                return;
            }
            
            int heldAmount = item.getAmount();
            int sellAmount;
            
            // 处理数量参数
            if (args.length >= 3) {
                sellAmount = Integer.parseInt(args[2]);
                if (sellAmount <= 0) {
                    player.sendMessage(ChatColor.RED + "数量必须大于0!");
                    return;
                }
                if (sellAmount > heldAmount) {
                    player.sendMessage(ChatColor.RED + "你手上没有足够的物品! 手持: " + heldAmount);
                    return;
                }
            } else {
                // 不填数量，上架全部
                sellAmount = heldAmount;
            }
            
            // 检查最大上架数量限制
            int maxListings = plugin.getConfig().getInt("max-listings-per-player", 10);
            if (maxListings > 0) { // -1 表示无限制
                int currentListings = plugin.getMarketManager().getPlayerListingCount(player.getUniqueId());
                if (currentListings >= maxListings) {
                    player.sendMessage(ChatColor.RED + "你已达到最大上架数量限制! " +
                            "当前: " + currentListings + "/" + maxListings + 
                            " 请先下架一些物品再上架新物品。");
                    return;
                }
            }
            
            // 检查上架费用
            double listingFeePercentage = plugin.getConfig().getDouble("listing-fee-percentage", 0.5);
            double listingFee = totalPrice * (listingFeePercentage / 100.0);
            
            // 检查玩家是否有足够的钱支付上架费用
            if (plugin.getEconomyManager().isEnabled() && listingFee > 0) {
                if (!plugin.getEconomyManager().hasBalance(player, listingFee)) {
                    player.sendMessage(ChatColor.RED + "你没有足够的金币支付上架费用! " +
                            "需要: " + plugin.getEconomyManager().formatCurrency(listingFee));
                    return;
                }
            }
            
            // 预验证阶段：在任何时候都不操作物品，只验证
            if (!player.isOnline()) {
                player.sendMessage(ChatColor.RED + "网络连接异常，请重试!");
                return;
            }
            
            // 验证物品存在性（只读操作，不修改物品）
            ItemStack currentItem = player.getInventory().getItemInMainHand();
            if (currentItem == null || currentItem.getType().isAir() || 
                !currentItem.isSimilar(item) || currentItem.getAmount() < sellAmount) {
                player.sendMessage(ChatColor.RED + "物品不足或状态异常!");
                return;
            }
            
            // 验证费用（只检查，不扣除）
            if (plugin.getEconomyManager().isEnabled() && listingFee > 0) {
                if (!plugin.getEconomyManager().hasBalance(player, listingFee)) {
                    player.sendMessage(ChatColor.RED + "你没有足够的金币支付上架费用! " +
                            "需要: " + plugin.getEconomyManager().formatCurrency(listingFee));
                    return;
                }
            }
            
            // 创建物品副本用于上架
            ItemStack sellItem = currentItem.clone();
            sellItem.setAmount(sellAmount);
            
            // 创建离线交易管理器
            OfflineTransactionManager offlineTransactionManager = new OfflineTransactionManager(
                plugin.getEconomyManager(), 
                SimpleOfflineInventory.getInstance()
            );
            
            // 先扣除物品，再扣除上架费用
            double totalCost = totalPrice; // 物品总价
            
            // 创建反物品复制管理器进行安全验证和扣除物品
            AntiDuplicationManager antiDupManager = new AntiDuplicationManager(plugin, offlineTransactionManager);
            AntiDuplicationManager.ValidationResult validationResult = antiDupManager.validateAndDeductItems(
                player, sellItem, sellAmount, totalCost
            );
            
            if (!validationResult.isSuccess()) {
                player.sendMessage(ChatColor.RED + validationResult.getMessage());
                return;
            }
            
            // 记录回滚数据（用于异常恢复）
            ItemStack[] rollbackItems = {sellItem.clone()};
            double rollbackMoney = totalCost;
            
            // 单独扣除上架费用（如果有）
            if (listingFee > 0) {
                if (!plugin.getEconomyManager().withdraw(player, listingFee).transactionSuccess()) {
                    // 如果扣税费失败，返还物品
                    ItemStack giveBack = sellItem.clone();
                    giveBack.setAmount(sellAmount);
                    player.getInventory().addItem(giveBack);
                    player.sendMessage(ChatColor.RED + "扣除上架费用失败!");
                    return;
                }
            }
            
            // 创建上架记录
            UUID listingId = plugin.getMarketManager().createListing(player, sellItem, totalPrice);
            
            // 4. 成功消息
            double pricePerItem = totalPrice / sellAmount;
            String successMessage = plugin.getConfig().getString("messages.create-success", 
                    "&a物品上架成功! 总价: %total% (%amount% x %count% 个)")
                    .replace("%total%", plugin.getEconomyManager().formatCurrency(totalPrice))
                    .replace("%amount%", plugin.getEconomyManager().formatCurrency(pricePerItem))
                    .replace("%count%", String.valueOf(sellAmount));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', successMessage));
            
            // 5. 税费提示
            if (listingFee > 0) {
                String feeMessage = plugin.getConfig().getString("messages.listing-fee-charged", 
                        "&a已收取上架费用: %fee% (物品价格的 %percentage%%)")
                        .replace("%fee%", plugin.getEconomyManager().formatCurrency(listingFee))
                        .replace("%percentage%", String.valueOf(plugin.getConfig().getDouble("listing-fee-percentage", 0.5)));
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', feeMessage));
            }
            
        } catch (NumberFormatException e) {
            if (e.getMessage().contains("integer")) {
                player.sendMessage(ChatColor.RED + "请输入有效的数量数字!");
            } else {
                player.sendMessage(ChatColor.RED + "请输入有效的价格数字!");
            }
        }
    }
    

    
    private void handleListingCommand(Player player) {
        plugin.getGUIManager().openMarketGUI(player, 0);
        player.sendMessage(ChatColor.GREEN + "已打开市场界面!");
    }
    

    
    private void handleReloadCommand(Player player) {
        if (!player.hasPermission("globalmarket.reload")) {
            player.sendMessage(ChatColor.RED + "你没有权限执行此命令!");
            return;
        }
        
        plugin.reloadPlugin();
        player.sendMessage(ChatColor.GREEN + "GlobalMarket 插件已重新加载!");
    }
    

    
    private void handleMailboxCommand(Player player) {
        if (!player.hasPermission("globalmarket.use")) {
            player.sendMessage(ChatColor.RED + "你没有权限使用邮箱!");
            return;
        }
        
        plugin.getMarketManager().getMailbox().openMailbox(player);
        player.sendMessage(ChatColor.GREEN + "已打开邮箱!");
    }
    
    private void handleSendCommand(Player player, String[] args) {
        if (!player.hasPermission("globalmarket.use")) {
            player.sendMessage(ChatColor.RED + "你没有权限使用邮寄功能!");
            return;
        }
        
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "用法: /market send <玩家> [数量]");
            return;
        }
        
        String targetPlayerName = args[1];
        Player targetPlayer = plugin.getServer().getPlayer(targetPlayerName);
        
        if (targetPlayer == null) {
            player.sendMessage(ChatColor.RED + "目标玩家不在线或不存在!");
            return;
        }
        
        if (targetPlayer.getName().equals(player.getName())) {
            player.sendMessage(ChatColor.RED + "你不能给自己邮寄物品!");
            return;
        }
        
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand == null || itemInHand.getType().isAir()) {
            player.sendMessage(ChatColor.RED + "你必须手持一个物品来邮寄!");
            return;
        }
        
        int heldAmount = itemInHand.getAmount();
        int sendAmount = heldAmount; // 默认发送全部
        
        // 处理数量参数
        if (args.length >= 3) {
            try {
                sendAmount = Integer.parseInt(args[2]);
                if (sendAmount <= 0) {
                    player.sendMessage(ChatColor.RED + "数量必须大于0!");
                    return;
                }
                if (sendAmount > heldAmount) {
                    player.sendMessage(ChatColor.RED + "你手上没有足够的物品! 手持: " + heldAmount);
                    return;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "请输入有效的数量数字!");
                return;
            }
        }
        
        // 创建物品副本
        ItemStack sendItem = itemInHand.clone();
        sendItem.setAmount(sendAmount);
        
        // 从玩家手中移除物品
        if (itemInHand.getAmount() > sendAmount) {
            itemInHand.setAmount(itemInHand.getAmount() - sendAmount);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
        
        // 添加到目标玩家的邮箱
        plugin.getMarketManager().getMailbox().addSentItemToMailbox(targetPlayer.getUniqueId(), sendItem, player.getName());
        
        String itemName = sendItem.getType().name();
        player.sendMessage(ChatColor.GREEN + "成功将 " + sendAmount + " 个 " + itemName + " 发送给 " + targetPlayer.getName() + "!");
        targetPlayer.sendMessage(ChatColor.GOLD + player.getName() + ChatColor.GREEN + " 给你邮寄了 " + sendAmount + " 个 " + itemName + "!");
        targetPlayer.sendMessage(ChatColor.GRAY + "使用 /market mail 查看邮箱");
    }
    
    private void handleSearchCommand(Player player, String[] args) {
        if (!player.hasPermission("globalmarket.use")) {
            player.sendMessage(ChatColor.RED + "你没有权限使用搜索功能!");
            return;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "用法: /market search <i:物品名|p:玩家名>");
            player.sendMessage(ChatColor.GRAY + "示例: /market search i:blaze_rod");
            player.sendMessage(ChatColor.GRAY + "示例: /market search p:Jeet");
            return;
        }

        String searchParam = args[1];
        
        if (searchParam.startsWith("i:")) {
            // 物品搜索
            String itemName = searchParam.substring(2).toUpperCase();
            plugin.getMarketManager().openItemSearchGUI(player, itemName);
            player.sendMessage(ChatColor.GREEN + "正在打开" + itemName + "的实时搜索结果...");
            
        } else if (searchParam.startsWith("p:")) {
            // 玩家搜索
            String playerName = searchParam.substring(2);
            plugin.getMarketManager().openPlayerSearchGUI(player, playerName);
            player.sendMessage(ChatColor.GREEN + "正在打开" + playerName + "的实时上架物品...");
            
        } else {
            player.sendMessage(ChatColor.RED + "无效的搜索格式! 使用 i:物品名 或 p:玩家名");
            player.sendMessage(ChatColor.GRAY + "示例: /market search i:blaze_rod");
            player.sendMessage(ChatColor.GRAY + "示例: /market search p:Jeet");
        }
    }

    private void showHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== GlobalMarket 帮助 ===");
        player.sendMessage(ChatColor.YELLOW + "/market create <总价> [数量]" + ChatColor.WHITE + " - 创建上架物品");
        player.sendMessage(ChatColor.YELLOW + "/market listing" + ChatColor.WHITE + " - 查看实时更新的市场列表");
        player.sendMessage(ChatColor.YELLOW + "/market mail" + ChatColor.WHITE + " - 打开邮箱管理物品");
        player.sendMessage(ChatColor.YELLOW + "/market send <玩家> [数量]" + ChatColor.WHITE + " - 邮寄物品到玩家邮箱");
        player.sendMessage(ChatColor.YELLOW + "/market search <i:物品名|p:玩家名>" + ChatColor.WHITE + " - 搜索特定物品或玩家上架");
        
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "=== 现代化功能 ===");
        player.sendMessage(ChatColor.GRAY + "• 使用listing指令查看实时更新的市场");
        player.sendMessage(ChatColor.GRAY + "• 使用search指令搜索特定物品或玩家上架");
        player.sendMessage(ChatColor.GRAY + "• 在市场中Shift+左键点击自己上架的物品可直接下架");
        player.sendMessage(ChatColor.GRAY + "• 下架的物品会存入邮箱并标记为[已下架]");
        player.sendMessage(ChatColor.GRAY + "• 使用send指令邮寄物品，收件人从邮箱取回");
        
        if (player.hasPermission("globalmarket.reload")) {
            player.sendMessage(ChatColor.YELLOW + "/market reload" + ChatColor.WHITE + " - 重新加载插件");
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.add("create");
            completions.add("listing");
            completions.add("mail");
            completions.add("send");
            completions.add("search");
            
            if (sender.hasPermission("globalmarket.reload")) {
                completions.add("reload");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("send")) {
            // 为send指令提供在线玩家名补全
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                completions.add(player.getName());
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("search")) {
            String currentArg = args[1];
            
            // 智能物品补全：只有手持物品时才提供i:补全
            if (sender instanceof Player) {
                Player player = (Player) sender;
                ItemStack itemInHand = player.getInventory().getItemInMainHand();
                
                if (itemInHand != null && !itemInHand.getType().isAir()) {
                    // 手持物品时提供i:补全
                    completions.add("i:" + itemInHand.getType().name().toLowerCase());
                }
            }
            
            // 始终提供p:前缀的在线玩家补全
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                completions.add("p:" + player.getName());
            }
            
            // 如果当前输入以i:开头但手持为空，则不提供i:补全
            if (currentArg.startsWith("i:") && sender instanceof Player) {
                Player player = (Player) sender;
                ItemStack itemInHand = player.getInventory().getItemInMainHand();
                if (itemInHand == null || itemInHand.getType().isAir()) {
                    // 移除i:相关的补全
                    completions.removeIf(s -> s.startsWith("i:"));
                }
            }
        }
        
        return completions;
    }
}