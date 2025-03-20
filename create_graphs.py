import matplotlib.pyplot as plt
import os

data_dir = "data"
output_dir = "graphs"
os.makedirs(output_dir, exist_ok=True)  # Ensure the output directory exists

for filename in os.listdir(data_dir):
    if filename.startswith("instance_") and filename.endswith(".txt"):  # Filter relevant files
        file_path = os.path.join(data_dir, filename)
        ks = []
        values = []
        
        with open(file_path, "r") as file:
            next(file)  # Skip header
            for line in file:
                parts = line.split()
                if len(parts) == 2:
                    k, value = map(float, parts)
                    ks.append(int(k))
                    values.append(value)
        
        # Plot the data
        plt.figure(figsize=(10, 5))
        plt.plot(ks, values, marker='o', linestyle='-', color='b', label='Value')
        plt.xlabel("|A|")
        plt.ylabel("Optimal")
        plt.title(f"|A| vs Optimal - {filename}")
        plt.legend()
        plt.grid(True)
        
        # Save the plot
        output_path = os.path.join(output_dir, f"{filename}.png")
        plt.savefig(output_path)
        plt.close()

print("Graphs have been generated and saved in the 'graphs' directory.")
